package io.accelerate.tracking.sync.credentials;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


public class AWSSecretPropertiesTest {

    @Test
    public void createClientShouldUseBasicSessionIfSessionTokenIsSet() {
        Properties properties = new Properties();
        properties.setProperty("trk_aws_access_key_id", "something");
        properties.setProperty("trk_aws_secret_access_key", "something");
        properties.setProperty("trk_s3_region", "us-east-1");
        properties.setProperty("trk_aws_session_token", "something");
        properties.setProperty("trk_s3_bucket", "bucket");
        
        AWSSecretProperties secretProperties = AWSSecretProperties.fromProperties(properties);
        AwsCredentials credentials = secretProperties.createCredentialsProvider().resolveCredentials();
        Assertions.assertTrue(credentials instanceof AwsSessionCredentials);

        S3AsyncClient client = secretProperties.createClient();
        client.close();
    }
    
    @Test
    public void createCredentialsProviderShouldUseWebIdentityWhenTokenPresent() {
        Properties properties = new Properties();
        properties.setProperty("trk_oidc_jwt_token", "original-token");
        properties.setProperty("trk_oidc_role_arn", "arn:aws:iam::123456789012:role/TestRole");
        properties.setProperty("trk_s3_region", "us-east-1");

        AWSSecretProperties secretProperties = AWSSecretProperties.fromProperties(properties, token -> token);

        Assertions.assertTrue(secretProperties.createCredentialsProvider()
                instanceof StsAssumeRoleWithWebIdentityCredentialsProvider);
    }

    @Test
    public void refreshRequestShouldUseRefreshedTokenOnEveryInvocation() {
        Properties properties = new Properties();
        properties.setProperty("trk_s3_region", "us-east-1");

        AtomicInteger invocationCount = new AtomicInteger();
        OidcTokenRefreshClient refreshClient = originalToken -> {
            Assertions.assertEquals("original-token", originalToken);
            return "refreshed-token-" + invocationCount.incrementAndGet();
        };

        AWSSecretProperties secretProperties = AWSSecretProperties.fromProperties(properties, refreshClient);

        Consumer<AssumeRoleWithWebIdentityRequest.Builder> refreshRequest =
                secretProperties.createRefreshRequest("arn:aws:iam::123456789012:role/TestRole", "session", "original-token");

        AssumeRoleWithWebIdentityRequest.Builder builderOne = AssumeRoleWithWebIdentityRequest.builder();
        refreshRequest.accept(builderOne);
        AssumeRoleWithWebIdentityRequest firstRequest = builderOne.build();

        Assertions.assertEquals("refreshed-token-1", firstRequest.webIdentityToken());
        Assertions.assertEquals("arn:aws:iam::123456789012:role/TestRole", firstRequest.roleArn());
        Assertions.assertEquals("session", firstRequest.roleSessionName());

        AssumeRoleWithWebIdentityRequest.Builder builderTwo = AssumeRoleWithWebIdentityRequest.builder();
        refreshRequest.accept(builderTwo);
        AssumeRoleWithWebIdentityRequest secondRequest = builderTwo.build();

        Assertions.assertEquals("refreshed-token-2", secondRequest.webIdentityToken());
        Assertions.assertEquals(2, invocationCount.get());
    }

    @Test
    public void fromPlainTextShouldThrowRuntimeIfFileNotFound() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            Path path = Paths.get("src/some_random_file_that_doesnot_exist.properties");
            AWSSecretProperties secretProperties = AWSSecretProperties.fromPlainTextFile(path);
        });
    }

}
