package io.accelerate.tracking.sync.credentials;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AWSSecretPropertiesTest {

    @Test
    public void createClientShouldUseBasicSessionIfSessionTokenIsSet() {
        Properties properties = new Properties();
        properties.setProperty("aws_access_key_id", "something");
        properties.setProperty("aws_secret_access_key", "something");
        properties.setProperty("s3_region", "us-east-1");
        properties.setProperty("aws_session_token", "something");
        
        AWSSecretProperties secretProperties = AWSSecretProperties.fromProperties(properties);
        S3Client client = secretProperties.createClient();
    }
    
    @Test
    public void fromPlainTextShouldThrowRuntimeIfFileNotFound() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            Path path = Paths.get("src/some_random_file_that_doesnot_exist.properties");
            AWSSecretProperties secretProperties = AWSSecretProperties.fromPlainTextFile(path);
        });
    }
}
