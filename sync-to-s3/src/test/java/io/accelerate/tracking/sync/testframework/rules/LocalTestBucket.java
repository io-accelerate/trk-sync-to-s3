package io.accelerate.tracking.sync.testframework.rules;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class LocalTestBucket extends TestBucket {

    public LocalTestBucket() {
        // Exactly the creds from your docker run
        AwsCredentialsProvider creds =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minio_access_key", "minio_secret_key"));

        this.amazonS3 = S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:9000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)   // v2 equivalent of forcePathStyle(true)
                        .build())
                .build();

        this.bucketName = "localbucket";
        this.uploadPrefix = "prefix/";

        ensureBucketExists(this.amazonS3, this.bucketName);
    }

    private static void ensureBucketExists(S3Client s3, String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        } catch (SdkClientException e) {
            throw new IllegalStateException("Cannot reach MinIO at http://127.0.0.1:9000: " + e.getMessage(), e);
        }
    }
}
