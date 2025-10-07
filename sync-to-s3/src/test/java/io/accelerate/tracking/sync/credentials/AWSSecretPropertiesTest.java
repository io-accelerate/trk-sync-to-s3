package io.accelerate.tracking.sync.credentials;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

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
        properties.setProperty("trk_oidc_jwt_token", "token");
        properties.setProperty("trk_oidc_role_arn", "arn:aws:iam::123456789012:role/TestRole");
        properties.setProperty("trk_s3_region", "us-east-1");

        AWSSecretProperties secretProperties = AWSSecretProperties.fromProperties(properties);

        Assertions.assertTrue(secretProperties.createCredentialsProvider()
                instanceof StsAssumeRoleWithWebIdentityCredentialsProvider);
    }

    @Test
    public void fromPlainTextShouldThrowRuntimeIfFileNotFound() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            Path path = Paths.get("src/some_random_file_that_doesnot_exist.properties");
            AWSSecretProperties secretProperties = AWSSecretProperties.fromPlainTextFile(path);
        });
    }
}
