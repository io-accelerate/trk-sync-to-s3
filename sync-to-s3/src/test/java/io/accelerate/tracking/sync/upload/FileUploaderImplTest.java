package io.accelerate.tracking.sync.upload;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.mockito.Mockito.*;

public class FileUploaderImplTest {

    @Test
    public void uploadShouldHandleFirstException() throws UploadingException, DestinationOperationException, IOException, URISyntaxException {
        Destination destination = mock(Destination.class);
        UploadingStrategy strategy = mock(UploadingStrategy.class);

        doThrow(new DestinationOperationException("Message"))
                .doNothing()
                .when(strategy)
                .upload(any(), anyString());

        FileUploader uploader = new FileUploaderImpl(strategy);
        File file = mock(File.class);
        when(file.toURI()).thenReturn(new URI("file:///tmp/file1.txt"));
        when(file.getName()).thenReturn("path");
        uploader.upload(file);
    }

    @Test
    public void uploadShouldThrowExceptionWhenFailsToAllRetries() throws UploadingException, DestinationOperationException, IOException, URISyntaxException {
        Assertions.assertThrows(UploadingException.class, () -> {
            Destination destination = mock(Destination.class);
            UploadingStrategy strategy = mock(UploadingStrategy.class);

            doThrow(new DestinationOperationException("Message"))
                    .when(strategy)
                    .upload(any(), anyString());

            FileUploader uploader = new FileUploaderImpl(strategy);
            File file = mock(File.class);
            when(file.toURI()).thenReturn(new URI("file:///tmp/file1.txt"));
            when(file.getName()).thenReturn("path");
            uploader.upload(file);
        });
    }
}
