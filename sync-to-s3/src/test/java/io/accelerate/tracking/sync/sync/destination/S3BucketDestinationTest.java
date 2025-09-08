package io.accelerate.tracking.sync.sync.destination;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.*;

public class S3BucketDestinationTest {

    private static final String SOME_BUCKET = "some_bucket";
    private static final String PREFIX = "prefix/";
    private S3Client awsClient;
    private AwsServiceException exception;
    private Destination destination;

    @BeforeEach
    public void setUp() {
        awsClient = mock(S3Client.class);
        exception = mock(AwsServiceException.class);
        destination = new S3BucketDestination(awsClient, SOME_BUCKET, PREFIX);
    }

    @Test
    public void startS3SyncSessionThrowsDestinationOperationException() throws DestinationOperationException {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            doThrow(exception).when(awsClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
            destination.startS3SyncSession();
        });
    }

    @Test
    public void stopS3SyncSessionThrowsDestinationOperationException() {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            doThrow(exception).when(awsClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
            destination.stopS3SyncSession();
        });
    }

    @Test
    public void initUploadingThrowsDestinationOperationException() {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            doThrow(exception).when(awsClient).createMultipartUpload(any(CreateMultipartUploadRequest.class));
            destination.initUploading("");
        });
    }

    @Test
    public void getAlreadyUploadedPartsThrowsDestinationOperationExceptionWhenListMultipartUploadsThrowsException() throws DestinationOperationException {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            doThrow(exception).when(awsClient).listMultipartUploads(any(ListMultipartUploadsRequest.class));
            destination.getAlreadyUploadedParts("");
        });
    }

    @Test
    public void getAlreadyUploadedPartsRunsNormalWhenNextListingThrowsException() throws DestinationOperationException {
        ListMultipartUploadsResponse listing = mock(ListMultipartUploadsResponse.class);
        when(awsClient.listMultipartUploads(any(ListMultipartUploadsRequest.class)))
                .thenReturn(listing)
                .thenThrow(exception);

        Assertions.assertTrue(destination.getAlreadyUploadedParts("").isEmpty());
    }

    @Test
    public void getAlreadyUploadedPartsRunsNormalWhenStreamNextListingThrowsException() throws DestinationOperationException {
        ListMultipartUploadsResponse listing = mock(ListMultipartUploadsResponse.class);
        when(listing.isTruncated()).thenReturn(false);
        when(awsClient.listMultipartUploads(any(ListMultipartUploadsRequest.class)))
                .thenReturn(listing)
                .thenThrow(exception);

        Assertions.assertTrue(destination.getAlreadyUploadedParts("").isEmpty());
    }

    @Test
    public void commitMultipartUploadThrowsDestinationOperationException() throws DestinationOperationException {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            doThrow(exception).when(awsClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
            List<CompletedPart> eTags = Arrays.asList(mock(CompletedPart.class), mock(CompletedPart.class));
            destination.commitMultipartUpload("", eTags, "");
        });
    }

    @Test
    public void uploadMultiPartThrowsDestinationOperationException() throws DestinationOperationException {
        Assertions.assertThrows(DestinationOperationException.class, () -> {
            UploadPartRequest request = mock(UploadPartRequest.class);
            RequestBody requestBody = mock(RequestBody.class);
            doThrow(exception).when(awsClient).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
            destination.uploadMultiPart(request, requestBody);
        });
    }

    @Test
    public void createUploadPartRequest() throws DestinationOperationException {
        Object newObject = destination.createUploadPartRequest("");
        MatcherAssert.assertThat(newObject, instanceOf(UploadPartRequest.class));
    }

    @Test
    public void filterUploadableFilesShouldAcceptAllIfS3DirectoryIsEmpty() throws DestinationOperationException {

        ListObjectsV2Response listObjectsV2Response = mock(ListObjectsV2Response.class);
        doReturn(List.of()).when(listObjectsV2Response).contents();

        doReturn(listObjectsV2Response).when(awsClient).listObjectsV2(any(ListObjectsV2Request.class));

        List<String> paths = Arrays.asList(
                "file1.txt",
                "file2.txt",
                "file3.txt",
                "file4.txt",
                "file5.txt",
                "file6.txt"
        );
        List<String> result = destination.filterUploadableFiles(paths);
        List<String> expected = Arrays.asList(
                "file1.txt",
                "file2.txt",
                "file3.txt",
                "file4.txt",
                "file5.txt",
                "file6.txt"
        );
        Collections.sort(result);
        Collections.sort(expected);
        Assertions.assertEquals(result, expected);
    }

    @Test
    public void filterUploadableFilesShouldRemoveFilesExistingInS3Directory() throws DestinationOperationException {

        ListObjectsV2Response listObjectsV2Response = mock(ListObjectsV2Response.class);

        List<String> existingPaths = Arrays.asList(
                PREFIX + "file1.txt",
                PREFIX + "subdir/file2.txt",
                PREFIX + "file6.txt"
        );
        
        List<S3Object> existingObjects = existingPaths.stream()
                .map(path -> {
                    S3Object s3Object = mock(S3Object.class);
                    doReturn(path).when(s3Object).key();
                    return s3Object;
                }).collect(Collectors.toList());

        doReturn(existingObjects).when(listObjectsV2Response).contents();
        doReturn(listObjectsV2Response).when(awsClient).listObjectsV2(any(ListObjectsV2Request.class));

        List<String> paths = Arrays.asList(
                "file1.txt",
                "subdir/file2.txt",
                "file3.txt",
                "subdir/file4.txt",
                "file5.txt",
                "file6.txt"
        );
        List<String> result = destination.filterUploadableFiles(paths);
        List<String> expected = Arrays.asList(
                "file3.txt",
                "subdir/file4.txt",
                "file5.txt"
        );
        Collections.sort(result);
        Collections.sort(expected);
        Assertions.assertEquals(result, expected);
    }

    @Test
    public void filterUploadableFilesShouldHandleMultipleMarkers() throws DestinationOperationException {

        ListObjectsV2Response listObjectsV2Response = mock(ListObjectsV2Response.class);

        List<String> existingPaths = Arrays.asList(
                PREFIX + "file1.txt",
                PREFIX + "subdir/file2.txt",
                PREFIX + "file6.txt",
                PREFIX + "file7.txt",
                PREFIX + "file8.txt",
                PREFIX + "file9.txt"
        );

        List<S3Object> existingObjects = existingPaths.stream()
                .map(path -> {
                    S3Object s3Object = mock(S3Object.class);
                    doReturn(path).when(s3Object).key();
                    return s3Object;
                }).collect(Collectors.toList());
        
        when(listObjectsV2Response.contents())
                .thenReturn(existingObjects);

        doReturn(listObjectsV2Response)
                .when(awsClient)
                .listObjectsV2(any(ListObjectsV2Request.class));

        List<String> paths = Arrays.asList(
                "file1.txt",
                "subdir/file2.txt",
                "file3.txt",
                "subdir/file4.txt",
                "file5.txt",
                "file6.txt",
                "file7.txt",
                "file8.txt",
                "file9.txt",
                "file10.txt",
                "file11.txt"
        );
        List<String> result = destination.filterUploadableFiles(paths);
        List<String> expected = Arrays.asList(
                "file3.txt",
                "subdir/file4.txt",
                "file5.txt",
                "file10.txt",
                "file11.txt"    
        );
        Collections.sort(result);
        Collections.sort(expected);
        Assertions.assertEquals(result, expected);
    }
}
