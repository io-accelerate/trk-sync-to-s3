package io.accelerate.tracking.sync.testframework;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;

public class LocalS3Test {

    public LocalTestBucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
    }

    @Test
    public void can_use_minio_server_correctly() {
        S3Client client = testBucket.getAmazonS3();
        String bucketName = "testbucket";

        // AWS SDK v2: Check for bucket existence using `headBucket`
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            // If bucket does not exist, create it
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }

        // AWS SDK v2: Upload an object using `PutObjectRequest`
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key("file/name")
                        .build(),
                RequestBody.fromBytes("contents".getBytes(StandardCharsets.UTF_8))
        );

        // AWS SDK v2: Get object attributes (`GetObjectAttributesRequest`)
        GetObjectAttributesResponse response = client.getObjectAttributes(GetObjectAttributesRequest.builder()
                .bucket(bucketName)
                .key("file/name")
                .objectAttributesWithStrings("ETag") // Request ETag specifically
                .build());

        Assertions.assertNotNull(response.eTag()); // Validate the presence of the ETag
    }
}