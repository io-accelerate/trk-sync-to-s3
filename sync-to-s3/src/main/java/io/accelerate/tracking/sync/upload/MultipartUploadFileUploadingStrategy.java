package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import io.accelerate.tracking.sync.sync.progress.DummyProgressListener;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import org.slf4j.Logger;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

public class MultipartUploadFileUploadingStrategy implements UploadingStrategy {
    private static final Logger log = getLogger(MultipartUploadFileUploadingStrategy.class);

    private static final int DEFAULT_THREAD_COUNT = 4;

    private Destination destination;

    private final ConcurrentMultipartUploader concurrentUploader;

    private ProgressListener listener = new DummyProgressListener();

    /**
     * Creates a new Multipart upload strategy.
     */
    public MultipartUploadFileUploadingStrategy(Destination destination) {
        this(destination, DEFAULT_THREAD_COUNT);
    }

    /**
     * Creates a new Multipart upload strategy with a custom thread count.
     *
     * @param threadsCount Number of threads to use for uploading.
     */
    private MultipartUploadFileUploadingStrategy(Destination destination, int threadsCount) {
        this.destination = destination;
        this.concurrentUploader = new ConcurrentMultipartUploader(destination, threadsCount);
    }

    @Override
    public void upload(File file, String remotePath) throws DestinationOperationException, IOException {
        MultipartUploadFile multipartUploadFile = new MultipartUploadFile(file, remotePath, destination);
        multipartUploadFile.validateUploadedFileSize();
        multipartUploadFile.notifyStart(listener);
        uploadRequiredParts(multipartUploadFile);
        multipartUploadFile.notifyFinish(listener);
    }

    private void uploadRequiredParts(MultipartUploadFile multipartUploadFile) throws IOException, DestinationOperationException {
        List<CompletedPart> completedParts = multipartUploadFile.getCompletedParts();

        Stream<UploadPartRequestAndBody> failedPartRequestStream = multipartUploadFile.streamUploadPartRequestForFailedParts();
        submitUploadRequestStream(failedPartRequestStream, completedParts);

        Stream<UploadPartRequestAndBody> incompletePartRequestStream = multipartUploadFile.streamUploadPartRequestForIncompleteParts();
        submitUploadRequestStream(incompletePartRequestStream, completedParts);

        concurrentUploader.shutdownAndAwaitTermination();

        multipartUploadFile.commitIfFinishedWriting();
    }

    private void submitUploadRequestStream(Stream<UploadPartRequestAndBody> requestStream, List<CompletedPart> completedParts) {
        requestStream
                .map(this::attachListenerToRequest)
                .map(uploadPartRequestAndBody -> concurrentUploader.submitTaskForPartUploading(
                        uploadPartRequestAndBody.getUploadPartRequest(), 
                        uploadPartRequestAndBody.getRequestBody()))
                .map(MultipartUploadFileUploadingStrategy::getUploadingResult)
                .filter(Objects::nonNull)
                .map(result -> CompletedPart.builder()
                        .partNumber(result.getRequest().partNumber())
                        .eTag(result.getResponse().eTag())
                        .build())
                .forEach(completedParts::add);
    }

    private UploadPartRequestAndBody attachListenerToRequest(UploadPartRequestAndBody request) {
        // SDK v2 does not support global listeners, so you may need to handle progress tracking in a separate way, if needed.
        listener.uploadFileProgress(request.getUploadPartRequest().uploadId(), 0); // Placeholder for manual progress tracking
        return request;
    }

    public static MultipartUploadResult getUploadingResult(Future<MultipartUploadResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            log.error("Some part uploads were unsuccessful.", e);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DestinationOperationException) {
                log.error("Some part uploads were unsuccessful.", cause);
            }
            log.error("Some part uploads were unsuccessful.", e);
            return null;
        }
    }

    @Override
    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    @Override
    public void setDestination(Destination destination) {
        this.destination = destination;
    }
}