package io.accelerate.tracking.sync.sync.destination;

import io.accelerate.tracking.sync.upload.MultipartUploadFinder;
import io.accelerate.tracking.sync.upload.MultipartUploadResult;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import java.util.*;
import java.util.stream.Collectors;

public class S3BucketDestination implements Destination {
    private final S3Client awsClient;
    private final String bucket;
    private final String prefix;

    public S3BucketDestination(S3Client awsClient, String bucket, String prefix) {
        this.awsClient = awsClient;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    // ~~~~ Public methods

    /**
     * If this method fails, stop everything.
     */
    public static void runSanityCheck() {
        AwsCredentialsProvider creds = DefaultCredentialsProvider.create(); // uses the default chain

        try (S3Client s3 = S3Client.builder()
                .region(Region.EU_WEST_2)
                .credentialsProvider(creds)
                .build()) {

            s3.getBucketAcl(GetBucketAclRequest.builder()
                    .bucket("ping.s3.accelerate.io")
                    .build());
        } catch (AwsServiceException e) {
            // Re-throw or log as startup failure
            throw new IllegalStateException("S3 sanity check failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public void startS3SyncSession() throws DestinationOperationException {
        try {
            String objectKey = prefix + "last_sync_start.txt";
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            RequestBody requestBody = RequestBody.fromString("timestamp: " + System.currentTimeMillis());
            PutObjectResponse putObjectResponse = awsClient.putObject(putObjectRequest, requestBody);
            if (!putObjectResponse.sdkHttpResponse().isSuccessful()) {
                throw new DestinationOperationException("Failed to write last_sync_start.txt");
            }
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to start S3 sync session", ex);
        }
    }

    @Override
    public void stopS3SyncSession() throws DestinationOperationException {
        try {
            String objectKey = prefix + "last_sync_stop.txt";
            awsClient.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build(), RequestBody.fromString("timestamp: " + System.currentTimeMillis())
            );
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to stop S3 sync session", ex);
        }
    }

    @Override
    public List<String> filterUploadableFiles(List<String> paths) {
        Set<String> existingItems = listAllObjects().stream()
                .map(S3Object::key)
                .collect(Collectors.toSet());

        int trimLength = prefix.length();
        return paths.stream()
                .map(path -> prefix + path)
                .filter(path -> !existingItems.contains(path))
                .map(path -> path.substring(trimLength))
                .collect(Collectors.toList());
    }

    private Set<S3Object> listAllObjects() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        ListObjectsV2Response result = awsClient.listObjectsV2(request);
        return new HashSet<>(result.contents());
    }

    @Override
    public String initUploading(String remotePath) throws DestinationOperationException {
        String fullPath = getFullPath(remotePath);
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(fullPath)
                    .build();

            CreateMultipartUploadResponse response = awsClient.createMultipartUpload(request);
            return response.uploadId();
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to initialize uploading: " + fullPath, ex);
        }
    }
    
    @Override
    public List<Part> getAlreadyUploadedParts(String remotePath) throws DestinationOperationException {
        MultipartUpload multipartUpload = findOrNull(remotePath);

        if (multipartUpload == null) {
            return Collections.emptyList();
        }
        
        String id = multipartUpload.uploadId();
        if (id == null) {
            return Collections.emptyList();
        }
        
        List<Part> parts = listUploadedParts(remotePath, id);
        if (parts == null) {
            return Collections.emptyList();
        }
        
        return parts;
    }

    @Override
    public MultipartUploadResult uploadMultiPart(UploadPartRequest request, RequestBody requestBody) throws DestinationOperationException {
        try {
            UploadPartResponse result = awsClient.uploadPart(request, requestBody);
            return new MultipartUploadResult(request, result);
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to upload multipart: " + request.key() + " #" + request.partNumber(), ex);
        }
    }

    @Override
    public void commitMultipartUpload(String remotePath, List<CompletedPart> eTags, String uploadId) throws DestinationOperationException {
        try {
            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(getFullPath(remotePath))
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(eTags).build())
                    .build();

            awsClient.completeMultipartUpload(request);
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to complete multipart upload", ex);
        }
    }

    @Override
    public UploadPartRequest createUploadPartRequest(String remotePath) {
        return UploadPartRequest.builder()
                .bucket(bucket)
                .key(getFullPath(remotePath))
                .build();
    }

    @Override
    public String getBucketName() {
        return bucket;
    }

    @Override
    public Optional<String> getExistingUploadId(String remotePath) throws DestinationOperationException {
        MultipartUpload multipartUpload = findOrNull(remotePath);

        if (multipartUpload == null) {
            return Optional.empty();
        }

        String id = multipartUpload.uploadId();
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    // ~~~ MultiPart Helpers

    private MultipartUpload findOrNull(String remotePath) throws DestinationOperationException {
        MultipartUploadFinder finder = new MultipartUploadFinder(awsClient, bucket, prefix);
        List<MultipartUpload> uploads = finder.getAlreadyStartedMultipartUploads();
        return uploads.stream()
                .filter(upload -> upload.key().equals(getFullPath(remotePath)))
                .findFirst()
                .orElse(null);
    }

    private List<Part> listUploadedParts(String remotePath, String uploadId) throws DestinationOperationException {
        try {
            ListPartsRequest request = ListPartsRequest.builder()
                    .bucket(bucket)
                    .key(getFullPath(remotePath))
                    .uploadId(uploadId)
                    .build();

            ListPartsResponse response = awsClient.listParts(request);
            return response.parts();
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException("Failed to list uploaded parts", ex);
        }
    }

    // ~~~ Path helpers

    private String getFullPath(String path) {
        return prefix + path;
    }
}