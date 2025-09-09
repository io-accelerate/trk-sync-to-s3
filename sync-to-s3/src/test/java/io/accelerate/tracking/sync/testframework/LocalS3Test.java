package io.accelerate.tracking.sync.testframework;

import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.concurrent.ExecutionException;

public class LocalS3Test {

    private LocalTestBucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
    }

    @Test
    void can_use_minio_server_correctly() throws Exception {
        try (S3AsyncClient s3 = testBucket.getS3AsyncClient()) {
            String bucket = "testbucket";

            ensureBucketExists(s3, bucket);

            // Upload object
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key("file/name")
                            .build(),
                    AsyncRequestBody.fromString("contents")
            ).get();

            // HeadObject
            HeadObjectResponse head = s3.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key("file/name")
                            .build()
            ).get();

            Assertions.assertNotNull(head.eTag());
        }
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
        }
    }
}
