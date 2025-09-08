package io.accelerate.tracking.sync.sync.destination;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import io.accelerate.tracking.sync.upload.MultipartUploadResult;

import java.util.List;
import java.util.Optional;

public class PerformanceMeasureDestination implements Destination {
    private final Destination destination;

    private int performanceScore = 0;

    public PerformanceMeasureDestination(Destination destination) {
        this.destination = destination;
    }

    public int getPerformanceScore() {
        return performanceScore;
    }

    @Override
    public void startS3SyncSession() throws DestinationOperationException {
        performanceScore += 2;
        destination.startS3SyncSession();
    }

    @Override
    public void stopS3SyncSession() throws DestinationOperationException {
        performanceScore += 2;
        destination.stopS3SyncSession();
    }

    @Override
    public String initUploading(String remotePath) throws DestinationOperationException {
        performanceScore += 1;
        return destination.initUploading(remotePath);
    }

    @Override
    public List<Part> getAlreadyUploadedParts(String remotePath) throws DestinationOperationException {
        performanceScore += 1;
        return destination.getAlreadyUploadedParts(remotePath);
    }

    @Override
    public MultipartUploadResult uploadMultiPart(UploadPartRequest request, RequestBody requestBody) throws DestinationOperationException {
        performanceScore += 1000;
        return destination.uploadMultiPart(request, requestBody);
    }

    @Override
    public void commitMultipartUpload(String remotePath, List<CompletedPart> eTags, String uploadId) throws DestinationOperationException {
        performanceScore += 1;
        destination.commitMultipartUpload(remotePath, eTags, uploadId);
    }

    @Override
    public UploadPartRequest createUploadPartRequest(String remotePath) throws DestinationOperationException {
        return destination.createUploadPartRequest(remotePath);
    }

    @Override
    public String getBucketName() {
        return destination.getBucketName();
    }

    @Override
    public Optional<String> getExistingUploadId(String remotePath) throws DestinationOperationException {
        return Optional.empty();
    }

    @Override
    public List<String> filterUploadableFiles(List<String> relativePaths) throws DestinationOperationException {
        performanceScore += 1;
        return destination.filterUploadableFiles(relativePaths);
    }
}