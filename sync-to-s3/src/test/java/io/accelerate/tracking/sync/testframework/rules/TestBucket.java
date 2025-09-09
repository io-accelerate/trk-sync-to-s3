package io.accelerate.tracking.sync.testframework.rules;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

abstract public class TestBucket {

    S3AsyncClient s3AsyncClient;
    String bucketName;
    String bucketPrefix;


    //~~~~ Getters
    public S3AsyncClient getS3AsyncClient() {
        return s3AsyncClient;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getBucketPrefix() {
        return bucketPrefix;
    }

    //~~~~ Lifecycle management
    public void beforeEach() {
        abortAllMultipartUploads();
        removeAllObjects();
    }

    private void removeAllObjects() {
        try {
            String continuation = null;
            do {
                ListObjectsV2Request.Builder lb = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(bucketPrefix);
                if (continuation != null) {
                    lb.continuationToken(continuation);
                }

                ListObjectsV2Response response = s3AsyncClient
                        .listObjectsV2(lb.build())
                        .get();

                if (response.hasContents() && !response.contents().isEmpty()) {
                    // Use DeleteObjects for batch deletion
                    List<ObjectIdentifier> toDelete = new ArrayList<>(response.contents().size());
                    for (S3Object obj : response.contents()) {
                        toDelete.add(ObjectIdentifier.builder().key(obj.key()).build());
                    }
                    DeleteObjectsRequest delReq = DeleteObjectsRequest.builder()
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(toDelete).build())
                            .build();
                    s3AsyncClient.deleteObjects(delReq).get();
                }

                continuation = response.isTruncated() ? response.nextContinuationToken() : null;
            } while (continuation != null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted removing objects", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed removing objects", e.getCause());
        }
    }

    private void abortAllMultipartUploads() {
        try {
            String keyMarker = null;
            String uploadIdMarker = null;
            boolean truncated;

            do {
                ListMultipartUploadsRequest.Builder b = ListMultipartUploadsRequest.builder()
                        .bucket(bucketName)
                        .prefix(bucketPrefix);
                if (keyMarker != null) b = b.keyMarker(keyMarker);
                if (uploadIdMarker != null) b = b.uploadIdMarker(uploadIdMarker);

                ListMultipartUploadsResponse resp = s3AsyncClient.listMultipartUploads(b.build()).get();

                for (MultipartUpload upload : resp.uploads()) {
                    AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(upload.key())
                            .uploadId(upload.uploadId())
                            .build();
                    s3AsyncClient.abortMultipartUpload(abortRequest).get();
                }

                truncated = Boolean.TRUE.equals(resp.isTruncated());
                keyMarker = resp.nextKeyMarker();
                uploadIdMarker = resp.nextUploadIdMarker();

            } while (truncated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted aborting MPUs", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed aborting MPUs", e.getCause());
        }
    }

    //~~~~ Bucket actions

    /** Check if an object exists. */
    public boolean doesObjectExists(String objectName) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(bucketPrefix + objectName)
                    .build();
            s3AsyncClient.headObject(request).get();
            return true;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchKeyException) return false;
            if (cause instanceof S3Exception s3e && s3e.statusCode() == 404) return false;
            throw new RuntimeException("HeadObject failed", cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted on HeadObject", ie);
        }
    }

    /** Get metadata for an object. */
    public HeadObjectResponse getObjectMetadata(String remoteName) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(buildKey(remoteName))
                    .build();
            return s3AsyncClient.headObject(request).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted getting object metadata", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed getting object metadata", e.getCause());
        }
    }

    /** Get details of multipart uploads for a key. */
    public Optional<MultipartUpload> getMultipartUploadFor(String remoteName) {
        try {
            ListMultipartUploadsRequest request = ListMultipartUploadsRequest.builder()
                    .bucket(bucketName)
                    .prefix(buildKey(remoteName))
                    .build();

            ListMultipartUploadsResponse response = s3AsyncClient.listMultipartUploads(request).get();

            return response.uploads().stream()
                    .filter(upload -> upload.key().equals(buildKey(remoteName)))
                    .findFirst();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted listing MPUs", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed listing MPUs", e.getCause());
        }
    }

    /** Get parts info for an object key's multipart upload. */
    public List<Part> getPartsForKey(String remoteName, String uploadId) {
        try {
            List<Part> parts = new ArrayList<>();
            Integer partNumberMarker = null;
            boolean truncated;
            do {
                ListPartsRequest.Builder b = ListPartsRequest.builder()
                        .bucket(bucketName)
                        .key(buildKey(remoteName))
                        .uploadId(uploadId);
                if (partNumberMarker != null) b.partNumberMarker(partNumberMarker);

                ListPartsResponse resp = s3AsyncClient.listParts(b.build()).get();
                parts.addAll(resp.parts());
                truncated = Boolean.TRUE.equals(resp.isTruncated());
                partNumberMarker = resp.nextPartNumberMarker();
            } while (truncated);

            return parts;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted listing parts", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed listing parts", e.getCause());
        }
    }

    /** Upload a single file. */
    @SuppressWarnings("SameParameterValue")
    public void upload(String remoteName, Path path) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(buildKey(remoteName))
                    .build();

            s3AsyncClient.putObject(putObjectRequest, AsyncRequestBody.fromFile(path)).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted on putObject", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed on putObject", e.getCause());
        }
    }

    /** Upload files from a directory. */
    public void uploadFilesInsideDir(Path dir) {
        // implement as needed for tests (walk the dir and call upload for each file)
    }

    /** Start a multipart upload. */
    public String initiateMultipartUpload(String remoteName) {
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(buildKey(remoteName))
                    .build();

            CreateMultipartUploadResponse response = s3AsyncClient.createMultipartUpload(request).get();
            return response.uploadId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted initiating MPU", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed initiating MPU", e.getCause());
        }
    }

    /** Upload a part in a multipart upload. */
    public void uploadPart(String remoteName, String uploadId, byte[] partData, int partNumber) throws NoSuchAlgorithmException {
        // Compute SHA256 digest of the part
        String partHash = computeSha256(partData);

        try {
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(buildKey(remoteName))
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength((long) partData.length)
                    .checksumSHA256(partHash) // <-- include checksum for server-side validation
                    .build();

            s3AsyncClient.uploadPart(request, AsyncRequestBody.fromBytes(partData)).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted uploading part", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed uploading part", e.getCause());
        }
    }


    /** Helper to compute SHA-256 hash. */
    private String computeSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }

    private String buildKey(String remoteFileName) {
        String p = this.bucketPrefix;
        if (p.isEmpty()) {
            return stripLeadingSlash(remoteFileName);
        }
        String normalizedPrefix = p.endsWith("/") ? p : p + "/";
        String name = stripLeadingSlash(remoteFileName);
        return normalizedPrefix + name;
    }

    private static String stripLeadingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
