package io.accelerate.tracking.sync.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class MultipartUploadFileTest {

    private Path testPath;

    private File mockFile;

    private Destination mockDestination;

    private String mockRemotePath;

    @BeforeEach
    public void setUp() throws Exception {
        testPath = Paths.get("src", "test", "resources", "test_dir");
        mockRemotePath = "file.txt";
        mockDestination = mock(Destination.class);
        mockFile = mock(File.class);
        when(mockFile.toPath()).thenReturn(testPath);
    }

    @Test
    public void validateUploadedFileSizeShouldThrowException() throws DestinationOperationException {
        when(mockFile.length()).thenReturn(Long.valueOf(-1));
        MultipartUploadFile multipartUploadFile = new MultipartUploadFile(mockFile, mockRemotePath, mockDestination);
        multipartUploadFile.getFile();
        multipartUploadFile.getUploadId();
        assertThrows(IllegalStateException.class, multipartUploadFile::validateUploadedFileSize);
    }

    @Test
    public void streamUploadPartRequestForFailedPartsShouldHandleIOException() throws DestinationOperationException, IOException {
        MultipartUploadFile multipartUploadFile = mock(MultipartUploadFile.class);
        Set<Integer> partNumbers = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));

        doReturn(partNumbers)
                .when(multipartUploadFile)
                .getFailedMiddlePartNumbers();
        doReturn(mock(UploadPartRequest.class))
                .when(multipartUploadFile)
                .getUploadPartRequestForData(any(), anyInt());
        doCallRealMethod().when(multipartUploadFile)
                .streamUploadPartRequestForFailedParts();

        Arrays.asList(1, 3, 5).stream().forEach(partNumber -> {
            try {
                when(multipartUploadFile.readPart(eq(partNumber)))
                        .thenReturn("Random".getBytes());
            } catch (IOException ex) {
                Logger.getLogger(MultipartUploadFileTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        Arrays.asList(2, 4).stream().forEach(partNumber -> {
            try {
                when(multipartUploadFile.readPart(eq(partNumber)))
                        .thenThrow(new IOException());
            } catch (IOException ex) {
                Logger.getLogger(MultipartUploadFileTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        List<UploadPartRequestAndBody> requests = multipartUploadFile.streamUploadPartRequestForFailedParts()
                .toList();

        assertEquals(3, requests.size());
    }
}
