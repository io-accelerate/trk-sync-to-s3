package io.accelerate.tracking.sync.credentials;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Read credentials and bucket information from private properties file.
 *
 * The file should contain the following keys:
 *  - aws_access_key_id
 *  - aws_secret_access_key
 *  - s3_region
 *  - s3_bucket
 */
public class AWSSecretProperties {
    private final Properties privateProperties;

    private AWSSecretProperties(Properties privateProperties) {
        this.privateProperties = privateProperties;
    }

    public static AWSSecretProperties fromPlainTextFile(Path plainTextPropertyFile) {
        return new AWSSecretProperties(loadPrivateProperties(plainTextPropertyFile));
    }

    public static AWSSecretProperties fromProperties(Properties privateProperties) {
        return new AWSSecretProperties(privateProperties);
    }

    /** Create an asynchronous S3 client. */
    public S3AsyncClient createClient() {
        String awsAccessKeyId = privateProperties.getProperty("aws_access_key_id");
        String awsSecretAccessKey = privateProperties.getProperty("aws_secret_access_key");
        String awsSessionToken = privateProperties.getProperty("aws_session_token"); // optional
        String s3Region = privateProperties.getProperty("s3_region");

        var awsCredentials = (awsSessionToken != null && !awsSessionToken.isBlank())
                ? AwsSessionCredentials.create(awsAccessKeyId, awsSecretAccessKey, awsSessionToken)
                : AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey);

        return S3AsyncClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .region(Region.of(s3Region))
                .build();
    }


    public String getS3Bucket() {
        return privateProperties.getProperty("s3_bucket");
    }

    public String getS3Prefix() {
        return privateProperties.getProperty("s3_prefix");
    }

    //~~~ Util

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
