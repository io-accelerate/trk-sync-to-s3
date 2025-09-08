package io.accelerate.tracking.sync.testframework.rules;

import io.accelerate.tracking.sync.sync.destination.DebugDestination;
import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.S3BucketDestination;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static io.accelerate.tracking.sync.testframework.rules.TemporarySyncFolder.PART_SIZE_IN_BYTES;

abstract public class TestBucket {

    S3Client amazonS3;
    String bucketName;
    String uploadPrefix;

    //~~~~ Getters
    public Destination asDestination() {
        return new S3BucketDestination(amazonS3, bucketName, uploadPrefix);
    }

    public S3Client getAmazonS3() {
        return amazonS3;
    }

    public String getBucketName() {
        return bucketName;
    }

    //~~~~ Lifecycle management
    public void beforeEach() {
        // Initialize the S3Client
        amazonS3 = S3Client.builder()
                .region(Region.US_EAST_1) // Replace with your region
                .build();

        // Additional setup can go here
    }

    private void removeAllObjects() {
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(uploadPrefix)
                .build();

        ListObjectsV2Response response = amazonS3.listObjectsV2(listObjectsRequest);

        response.contents().forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Object.key())
                    .build();
            amazonS3.deleteObject(deleteObjectRequest);
        });
    }

    private void abortAllMultipartUploads() {
        ListMultipartUploadsRequest listMultipartUploadsRequest = ListMultipartUploadsRequest.builder()
                .bucket(bucketName)
                .prefix(uploadPrefix)
                .build();

        ListMultipartUploadsResponse response = amazonS3.listMultipartUploads(listMultipartUploadsRequest);

        response.uploads().forEach(upload -> {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(upload.key())
                    .uploadId(upload.uploadId())
                    .build();
            amazonS3.abortMultipartUpload(abortRequest);
        });
    }

    //~~~~ Bucket actions

    /**
     * Check if an object exists.
     */
    public boolean doesObjectExists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            amazonS3.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Get metadata for an object.
     */
    public HeadObjectResponse getObjectMetadata(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return amazonS3.headObject(request);
    }

    /**
     * Get details of multipart uploads for a key.
     */
    public Optional<MultipartUpload> getMultipartUploadFor(String key) {
        ListMultipartUploadsRequest request = ListMultipartUploadsRequest.builder()
                .bucket(bucketName)
                .prefix(key)
                .build();

        ListMultipartUploadsResponse response = amazonS3.listMultipartUploads(request);

        return response.uploads().stream()
                .filter(upload -> upload.key().equals(key))
                .findFirst();
    }

    /**
     * Get parts info for an object key's multipart upload.
     */
    public List<Part> getPartsForKey(String key, String uploadId) {
        
        
        ListPartsRequest request = ListPartsRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .build();

        ListPartsResponse response = amazonS3.listParts(request);

        return response.parts();
    }

    /**
     * Upload a single file.
     */
    @SuppressWarnings("SameParameterValue")
    public void upload(String key, Path path) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        amazonS3.putObject(putObjectRequest, RequestBody.fromFile(path));
    }

    /**
     * Upload files from a directory.
     */
    public void uploadFilesInsideDir(Path dir) {
        // Implementation can recursively iterate over all files in `dir` and call `upload(...)` for each file
    }

    /**
     * Start a multipart upload.
     */
    public String initiateMultipartUpload(String name) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(name)
                .build();

        CreateMultipartUploadResponse response = amazonS3.createMultipartUpload(request);
        return response.uploadId();
    }

    /**
     * Upload a part in a multipart upload.
     */
    public void uploadPart(String name, String uploadId, byte[] partData, int partNumber) throws NoSuchAlgorithmException {
        // Compute SHA256 digest of the part
        String partHash = computeSha256(partData);

        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(name)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength((long) partData.length)
                .build();

        amazonS3.uploadPart(request, RequestBody.fromBytes(partData));
    }

    /**
     * Helper to compute SHA-256 hash.
     */
    private String computeSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }
}
