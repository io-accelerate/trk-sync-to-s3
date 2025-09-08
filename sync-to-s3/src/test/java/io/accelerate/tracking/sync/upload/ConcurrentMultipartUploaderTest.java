package io.accelerate.tracking.sync.upload;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.*;

public class ConcurrentMultipartUploaderTest {

    @Test
    public void constructorShouldThrowExceptionOnInvalidThreadCount() throws IllegalArgumentException {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Destination destination = mock(Destination.class);
            new ConcurrentMultipartUploader(destination, 0);
        });
    }

    @Test
    public void testExecutor() {
        Destination destination = mock(Destination.class);
        ConcurrentMultipartUploader uploader = new ConcurrentMultipartUploader(destination);
        ExecutorService executorService = uploader.getExecutorService();
        Assertions.assertNotNull(executorService);
    }

    @Test
    public void executionShouldHandleException() throws DestinationOperationException, InterruptedException {
        Destination destination = mock(Destination.class);
        MultipartUploadResult result = mock(MultipartUploadResult.class);
        when(destination.uploadMultiPart(any(), any()))
                .thenReturn(result)
                .thenThrow(new DestinationOperationException(""));

        ConcurrentMultipartUploader uploader = new ConcurrentMultipartUploader(destination);

        List<UploadPartRequestAndBody> requests = List.of(
                new UploadPartRequestAndBody(mock(UploadPartRequest.class),mock(RequestBody.class)),
                new UploadPartRequestAndBody(mock(UploadPartRequest.class),mock(RequestBody.class))
        );
        List<MultipartUploadResult> streamResult = requests.stream()
                .map((UploadPartRequestAndBody request) -> uploader.submitTaskForPartUploading(request.getUploadPartRequest(), request.getRequestBody()))
                .map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException ex) {
                        return null;
                    } catch (ExecutionException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Assertions.assertEquals(streamResult.size(), 1);
        uploader.shutdownAndAwaitTermination();
    }

    @Test
    public void executionShouldHandleInterruption() throws DestinationOperationException, InterruptedException, ExecutionException, TimeoutException {
        DestinationOperationException destinationOperationException = Assertions.assertThrows(DestinationOperationException.class, () -> {
            ConcurrentMultipartUploader uploader = mock(ConcurrentMultipartUploader.class);
            ExecutorService service = mock(ExecutorService.class);
            when(service.awaitTermination(anyLong(), any()))
                    .thenThrow(mock(InterruptedException.class));
            when(uploader.getExecutorService()).thenReturn(service);

            doCallRealMethod().when(uploader)
                    .shutdownAndAwaitTermination();

            uploader.shutdownAndAwaitTermination();
        });
        MatcherAssert.assertThat(destinationOperationException.getMessage(), containsString("Cannot finish uploading"));
    }
}
