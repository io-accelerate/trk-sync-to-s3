package io.accelerate.tracking.sync.testframework.rules;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.net.URI;

public class LocalTestBucket extends TestBucket {

    public LocalTestBucket() {
        // Create a credential provider
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                "minio_access_key",
                "minio_secret_key"
        );

        // Build the S3 client
        amazonS3 = S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:9000")) // Set endpoint
                .region(Region.US_EAST_1) // Set region
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true) // Enable path-style access
                .build();

        // Initialize bucket name
        bucketName = "localbucket";

        // Check if the bucket exists, if not create it
        if (!doesBucketExist(bucketName)) {
            amazonS3.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        }

        // Set upload prefix
        uploadPrefix = "prefix/";
    }

    private boolean doesBucketExist(String bucketName) {
        // List all buckets and check if our bucket exists
        ListBucketsResponse bucketsResponse = amazonS3.listBuckets();
        for (Bucket bucket : bucketsResponse.buckets()) {
            if (bucket.name().equals(bucketName)) {
                return true;
            }
        }
        return false;
    }
}