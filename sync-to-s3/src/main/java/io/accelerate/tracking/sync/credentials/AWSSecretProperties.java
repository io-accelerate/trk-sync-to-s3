package io.accelerate.tracking.sync.credentials;

import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Read credentials and bucket information from private properties file.
 *
 * The file should contain the following keys:
 *  - trk_s3_region
 *  - trk_s3_bucket (or trk_upload_bucket for legacy configs)
 *  - trk_s3_prefix (optional)
 *
 * For static or STS-style credentials:
 *  - trk_aws_access_key_id
 *  - trk_aws_secret_access_key
 *  - trk_aws_session_token (optional)
 *
 * For web identity credentials:
 *  - trk_oidc_jwt_token
 *  - trk_oidc_role_arn
 *  - trk_oidc_role_session_name (optional)
 *  - trk_oidc_sts_region (optional)
 */
public class AWSSecretProperties {
    private static final Logger log = getLogger(AWSSecretProperties.class);

    private static final String KEY_S3_REGION = "trk_s3_region";
    private static final String KEY_S3_BUCKET = "trk_s3_bucket";
    private static final String KEY_UPLOAD_BUCKET = "trk_upload_bucket";
    private static final String KEY_S3_PREFIX = "trk_s3_prefix";

    private static final String KEY_AWS_ACCESS_KEY_ID = "trk_aws_access_key_id";
    private static final String KEY_AWS_SECRET_ACCESS_KEY = "trk_aws_secret_access_key";
    private static final String KEY_AWS_SESSION_TOKEN = "trk_aws_session_token";

    private static final String KEY_OIDC_TOKEN = "trk_oidc_jwt_token";
    private static final String KEY_OIDC_ROLE_ARN = "trk_oidc_role_arn";
    private static final String KEY_OIDC_ROLE_SESSION_NAME = "trk_oidc_role_session_name";
    private static final String KEY_OIDC_STS_REGION = "trk_oidc_sts_region";

    private final Properties privateProperties;
    private final OidcTokenRefreshClient oidcTokenRefreshClient;

    private AWSSecretProperties(Properties privateProperties) {
        this(privateProperties, new HttpOidcTokenRefreshClient(HttpClient.newHttpClient()));
    }

    AWSSecretProperties(Properties privateProperties, OidcTokenRefreshClient oidcTokenRefreshClient) {
        this.privateProperties = privateProperties;
        this.oidcTokenRefreshClient = Objects.requireNonNull(oidcTokenRefreshClient, "oidcTokenRefreshClient");
    }

    public static AWSSecretProperties fromPlainTextFile(Path plainTextPropertyFile) {
        return new AWSSecretProperties(loadPrivateProperties(plainTextPropertyFile));
    }

    public static AWSSecretProperties fromProperties(Properties privateProperties) {
        return new AWSSecretProperties(privateProperties);
    }

    static AWSSecretProperties fromProperties(Properties privateProperties, OidcTokenRefreshClient oidcTokenRefreshClient) {
        return new AWSSecretProperties(privateProperties, oidcTokenRefreshClient);
    }

    /** Create an asynchronous S3 client. */
    public S3AsyncClient createClient() {
        AwsCredentialsProvider credentialsProvider = createCredentialsProvider();
        String s3Region = requireNonBlank(KEY_S3_REGION);

        log.debug("Creating S3 async client for region '" + s3Region + "'.");

        return S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(s3Region))
                .build();
    }


    AwsCredentialsProvider createCredentialsProvider() {
        log.debug("Selecting AWS credentials provider. Available credential keys: " + describeAvailableCredentialKeys());
        String oidcToken = getTrimmed(KEY_OIDC_TOKEN);
        if (oidcToken != null) {
            log.info("Using web identity credentials provider.");
            return createWebIdentityCredentialsProvider(oidcToken);
        } else {
            log.info("Using static AWS credentials provider.");
        }

        log.debug("Using static AWS credentials provider. Session token present: " + (getTrimmed(KEY_AWS_SESSION_TOKEN) != null));
        return createStaticCredentialsProvider();
    }

    private AwsCredentialsProvider createStaticCredentialsProvider() {
        String accessKey = requireNonBlank(KEY_AWS_ACCESS_KEY_ID);
        String secretKey = requireNonBlank(KEY_AWS_SECRET_ACCESS_KEY);
        String sessionToken = getTrimmed(KEY_AWS_SESSION_TOKEN);

        log.info("Constructed static credentials provider. Session token present: " + (sessionToken != null));

        return (sessionToken != null)
                ? StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private AwsCredentialsProvider createWebIdentityCredentialsProvider(String oidcToken) {
        String roleArn = requireNonBlank(KEY_OIDC_ROLE_ARN);
        String sessionName = getTrimmed(KEY_OIDC_ROLE_SESSION_NAME);
        String resolvedSessionName = sessionName != null ? sessionName : defaultSessionName();

        String stsRegion = getTrimmed(KEY_OIDC_STS_REGION);
        String resolvedStsRegion = stsRegion != null ? stsRegion : requireNonBlank(KEY_S3_REGION);

        StsAssumeRoleWithWebIdentityCredentialsProvider.Builder builder = StsAssumeRoleWithWebIdentityCredentialsProvider.builder()
                .refreshRequest(createRefreshRequest(roleArn, resolvedSessionName, oidcToken))
                .asyncCredentialUpdateEnabled(true);

        StsClientBuilder stsClientBuilder = StsClient.builder()
                .region(Region.of(resolvedStsRegion))
                .credentialsProvider(AnonymousCredentialsProvider.create());

        builder.stsClient(stsClientBuilder.build());

        log.debug("Constructed web identity credentials provider for role '" + roleArn
                + "', session name '" + resolvedSessionName + "', sts region '" + resolvedStsRegion
                + "'. Session name provided: " + (sessionName != null));

        return builder.build();
    }

    Consumer<AssumeRoleWithWebIdentityRequest.Builder> createRefreshRequest(String roleArn,
                                                                            String resolvedSessionName,
                                                                            String originalOidcToken) {
        return requestBuilder -> {
            String refreshedToken = oidcTokenRefreshClient.refresh(originalOidcToken);
            requestBuilder.roleArn(roleArn)
                    .roleSessionName(resolvedSessionName)
                    .webIdentityToken(refreshedToken);
        };
    }

    private static String defaultSessionName() {
        return "trk-sync-to-s3-" + UUID.randomUUID();
    }

    public String getS3Bucket() {
        String bucket = getTrimmed(KEY_S3_BUCKET);
        if (bucket != null) {
            return bucket;
        }
        return getTrimmed(KEY_UPLOAD_BUCKET);
    }

    public String getS3Prefix() {
        return getTrimmed(KEY_S3_PREFIX);
    }

    //~~~ Util

    private String describeAvailableCredentialKeys() {
        List<String> availableKeys = new ArrayList<>();
        if (hasValue(KEY_AWS_ACCESS_KEY_ID)) {
            availableKeys.add(KEY_AWS_ACCESS_KEY_ID);
        }
        if (hasValue(KEY_AWS_SECRET_ACCESS_KEY)) {
            availableKeys.add(KEY_AWS_SECRET_ACCESS_KEY);
        }
        if (hasValue(KEY_AWS_SESSION_TOKEN)) {
            availableKeys.add(KEY_AWS_SESSION_TOKEN);
        }
        if (hasValue(KEY_OIDC_TOKEN)) {
            availableKeys.add(KEY_OIDC_TOKEN);
        }
        if (hasValue(KEY_OIDC_ROLE_ARN)) {
            availableKeys.add(KEY_OIDC_ROLE_ARN);
        }
        if (hasValue(KEY_OIDC_ROLE_SESSION_NAME)) {
            availableKeys.add(KEY_OIDC_ROLE_SESSION_NAME);
        }
        if (hasValue(KEY_OIDC_STS_REGION)) {
            availableKeys.add(KEY_OIDC_STS_REGION);
        }
        return availableKeys.isEmpty() ? "none" : String.join(", ", availableKeys);
    }

    private boolean hasValue(String key) {
        return getTrimmed(key) != null;
    }

    private String requireNonBlank(String key) {
        String value = getTrimmed(key);
        if (value == null) {
            throw new IllegalStateException("Missing required property '" + key + "'");
        }
        return value;
    }

    private String getTrimmed(String key) {
        String value = privateProperties.getProperty(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Properties loadPrivateProperties(Path privatePropertiesPath) {
        Properties properties = new Properties();
        try (InputStream inStream = Files.newInputStream(privatePropertiesPath)) {
            properties.load(inStream);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
