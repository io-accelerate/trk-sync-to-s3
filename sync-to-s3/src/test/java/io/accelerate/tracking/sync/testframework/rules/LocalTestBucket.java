package io.accelerate.tracking.sync.testframework.rules;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class LocalTestBucket extends TestBucket {

    public LocalTestBucket() {
        AwsCredentialsProvider creds =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minio_access_key", "minio_secret_key"));

        this.s3AsyncClient = S3AsyncClient.builder()
                .endpointOverride(URI.create("http://127.0.0.1:9000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        this.bucketName = "localbucket";
        this.bucketPrefix = "prefix/";

        ensureBucketExists(this.s3AsyncClient, this.bucketName);
    }

    private static void ensureBucketExists(S3AsyncClient s3, String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build()).get();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof S3Exception s3e && s3e.statusCode() == 404) {
                try {
                    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).get();
                } catch (Exception inner) {
                    throw new IllegalStateException("Failed to create bucket " + bucket, inner);
                }
            } else {
                throw new IllegalStateException("HeadBucket failed for " + bucket, cause);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted ensuring bucket exists for " + bucket, ie);
        } catch (SdkClientException e) {
            throw new IllegalStateException("Cannot reach MinIO at http://127.0.0.1:9000: " + e.getMessage(), e);
        }
    }
}
