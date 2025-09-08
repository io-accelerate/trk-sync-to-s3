package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.util.concurrent.*;

public class ConcurrentMultipartUploader {

    private static final int DEFAULT_THREAD_COUNT = 4;

    private static final int MAX_UPLOADING_TIME = 360;

    private final Destination destination;

    private final ExecutorService executorService;

    public ConcurrentMultipartUploader(Destination destination) {
        this(destination, DEFAULT_THREAD_COUNT);
    }

    ConcurrentMultipartUploader(Destination destination, int threadCount) {
        this.destination = destination;
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count should be >= 1");
        }
        executorService = Executors.newFixedThreadPool(threadCount);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    void shutdownAndAwaitTermination() throws DestinationOperationException {
        ExecutorService service = getExecutorService();
        service.shutdown();
        try {
            service.awaitTermination(MAX_UPLOADING_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new DestinationOperationException("Cannot finish uploading", ex);
        }
    }

    Future<MultipartUploadResult> submitTaskForPartUploading(UploadPartRequest request, RequestBody requestBody) {
        Callable<MultipartUploadResult> task = createCallableForPartUploadingAndReturnETag(request, requestBody);
        return getExecutorService().submit(task);
    }

    private Callable<MultipartUploadResult> createCallableForPartUploadingAndReturnETag(UploadPartRequest request, RequestBody requestBody) {
        return () -> {
            try {
                return destination.uploadMultiPart(request, requestBody);
            } catch (DestinationOperationException e) {
                throw e;
            }
        };
    }
}
