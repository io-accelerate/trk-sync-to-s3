package io.accelerate.tracking.sync.testframework;

import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class LocalS3Test {

    private LocalTestBucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
    }

    @Test
    void can_use_minio_server_correctly() {
        try (S3Client s3 = testBucket.getAmazonS3()) {
            String bucket = "testbucket";

            ensureBucketExists(s3, bucket);

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key("file/name")
                            .build(),
                    RequestBody.fromString("contents")
            );

            HeadObjectResponse head =
                    s3.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key("file/name")
                            .build());

            Assertions.assertNotNull(head.eTag());
        }
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
        }
    }
}
