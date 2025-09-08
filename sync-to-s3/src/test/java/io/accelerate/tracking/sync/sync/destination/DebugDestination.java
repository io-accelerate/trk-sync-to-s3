package io.accelerate.tracking.sync.sync.destination;

import org.slf4j.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import io.accelerate.tracking.sync.upload.MultipartUploadResult;

import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class DebugDestination implements Destination {
    private static final Logger log = getLogger(DebugDestination.class);

    private final Destination destination;

    public DebugDestination(Destination destination) {
        this.destination = destination;
    }

    @Override
    public void startS3SyncSession() throws DestinationOperationException {
        log.debug("startS3SyncSession: START");
        destination.startS3SyncSession();
        log.debug("startS3SyncSession: FINISH");
    }

    @Override
    public void stopS3SyncSession() throws DestinationOperationException {
        log.debug("stopS3SyncSession: START");
        destination.stopS3SyncSession();
        log.debug("stopS3SyncSession: FINISH");
    }

    @Override
    public String initUploading(String remotePath) throws DestinationOperationException {
        log.debug("initUploading: START");
        String result = destination.initUploading(remotePath);
        log.debug("initUploading: FINISH");
        return result;
    }

    @Override
    public List<Part> getAlreadyUploadedParts(String remotePath) throws DestinationOperationException {
        log.debug("getAlreadyUploadedParts: START");
        List<Part> result = destination.getAlreadyUploadedParts(remotePath);
        log.debug("getAlreadyUploadedParts: FINISH");
        return result;
    }

    @Override
    public MultipartUploadResult uploadMultiPart(UploadPartRequest request, RequestBody requestBody) throws DestinationOperationException {
        log.debug("uploadMultiPart: START");
        MultipartUploadResult result = destination.uploadMultiPart(request, requestBody);
        log.debug("uploadMultiPart: FINISH");
        return result;
    }

    @Override
    public void commitMultipartUpload(String remotePath, List<CompletedPart> eTags, String uploadId) throws DestinationOperationException {
        log.debug("commitMultipartUpload: START");
        destination.commitMultipartUpload(remotePath, eTags, uploadId);
        log.debug("commitMultipartUpload: FINISH");
    }

    @Override
    public UploadPartRequest createUploadPartRequest(String remotePath) throws DestinationOperationException {
        log.debug("createUploadPartRequest: START");
        UploadPartRequest request = destination.createUploadPartRequest(remotePath);
        log.debug("createUploadPartRequest: FINISH");
        return request;
    }

    @Override
    public String getBucketName() {
        log.debug("getBucketName: START");
        String bucketName = destination.getBucketName();
        log.debug("getBucketName: FINISH");
        return bucketName;
    }

    @Override
    public Optional<String> getExistingUploadId(String remotePath) throws DestinationOperationException {
        return Optional.empty();
    }

    @Override
    public List<String> filterUploadableFiles(List<String> relativePaths) throws DestinationOperationException {
        log.debug("canUploadFiles: START");
        List<String> result = destination.filterUploadableFiles(relativePaths);
        log.debug("canUploadFiles: FINISH");
        return result;
    }
}