package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.*;

public class MultipartUploadFinderTest {

    @Test
    public void getAlreadyStartedMultipartUploads() throws DestinationOperationException {
        // Mock the S3Client and ListMultipartUploadsResponse
        S3Client s3Client = mock(S3Client.class);
        ListMultipartUploadsResponse response = ListMultipartUploadsResponse.builder()
                .uploads(Collections.emptyList())
                .isTruncated(false)
                .build();

        // Mock the behavior of the S3 client's listMultipartUploads method
        when(s3Client.listMultipartUploads(any(ListMultipartUploadsRequest.class)))
                .thenReturn(response);

        // Define bucket and prefix
        String bucket = "bucket";
        String prefix = "prefix";

        MultipartUploadFinder finder = new MultipartUploadFinder(s3Client, bucket, prefix);
        finder.getAlreadyStartedMultipartUploads();
    }

    @Test
    public void getAlreadyStartedMultipartUploadShouldThrowDestinationOperationException() throws DestinationOperationException {
        DestinationOperationException destinationOperationException = Assertions.assertThrows(DestinationOperationException.class, () -> {
            // Mock the S3Client
            S3Client s3Client = mock(S3Client.class);

            // Simulate S3Exception
            when(s3Client.listMultipartUploads(any(ListMultipartUploadsRequest.class)))
                    .thenThrow(S3Exception.builder().message("Message").build());

            String bucket = "bucket";
            String prefix = "prefix";

            MultipartUploadFinder finder = new MultipartUploadFinder(s3Client, bucket, prefix);
            finder.getAlreadyStartedMultipartUploads();
        });

        MatcherAssert.assertThat(destinationOperationException.getMessage(),
                containsString("Failed to list multipart uploads:"));
    }

    @Test
    public void getAlreadyStartedMultipartUploadsShouldIterateMultipleListing() throws DestinationOperationException {
        // Mock the S3Client and responses
        S3Client s3Client = mock(S3Client.class);

        // First response - truncated
        ListMultipartUploadsResponse firstResponse = ListMultipartUploadsResponse.builder()
                .uploads(Collections.emptyList())
                .isTruncated(true)
                .build();

        // Second response - not truncated
        ListMultipartUploadsResponse secondResponse = ListMultipartUploadsResponse.builder()
                .uploads(Collections.emptyList())
                .isTruncated(false)
                .build();

        // Mock behavior for multiple calls
        when(s3Client.listMultipartUploads(Mockito.any(ListMultipartUploadsRequest.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        String bucket = "bucket";
        String prefix = "prefix";

        MultipartUploadFinder finder = new MultipartUploadFinder(s3Client, bucket, prefix);
        finder.getAlreadyStartedMultipartUploads();

        // Verify multiple calls to listMultipartUploads
        verify(s3Client, times(2)).listMultipartUploads(any(ListMultipartUploadsRequest.class));
    }

    @Test
    public void getAlreadyStartedMultipartUploadsShouldIterateMultipleListingWithException() throws DestinationOperationException {
        // Mock the S3Client and responses
        S3Client s3Client = mock(S3Client.class);

        // First response - truncated
        ListMultipartUploadsResponse firstResponse = ListMultipartUploadsResponse.builder()
                .uploads(Collections.emptyList())
                .isTruncated(true)
                .build();

        // Simulate exception on the second call
        when(s3Client.listMultipartUploads(Mockito.any(ListMultipartUploadsRequest.class)))
                .thenReturn(firstResponse)
                .thenThrow(S3Exception.builder().message("Message").build());

        String bucket = "bucket";
        String prefix = "prefix";

        MultipartUploadFinder finder = new MultipartUploadFinder(s3Client, bucket, prefix);

        // Call the method (this should handle the exception gracefully)
        finder.getAlreadyStartedMultipartUploads();

        // Verify that the listMultipartUploads was called twice
        verify(s3Client, times(2)).listMultipartUploads(any(ListMultipartUploadsRequest.class));
    }
}