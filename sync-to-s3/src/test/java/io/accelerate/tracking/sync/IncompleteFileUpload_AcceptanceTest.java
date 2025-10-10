package io.accelerate.tracking.sync;

import io.accelerate.tracking.sync.helpers.FormattingHelper;
import io.accelerate.tracking.sync.sync.Filters;
import io.accelerate.tracking.sync.sync.RemoteSync;
import io.accelerate.tracking.sync.sync.Source;
import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;
import io.accelerate.tracking.sync.testframework.rules.TemporarySyncFolder;
import io.accelerate.tracking.sync.testframework.listeners.RecordingProgressListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.model.MultipartUpload;
import software.amazon.awssdk.services.s3.model.Part;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.accelerate.tracking.sync.helpers.FormattingHelper.sanitizeETag;
import static org.hamcrest.CoreMatchers.is;
import static io.accelerate.tracking.sync.testframework.rules.TemporarySyncFolder.ONE_MEGABYTE;
import static io.accelerate.tracking.sync.testframework.rules.TemporarySyncFolder.PART_SIZE_IN_BYTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

public class IncompleteFileUpload_AcceptanceTest {

    private Filters defaultFilters;

    public TemporarySyncFolder targetSyncFolder;

    public LocalTestBucket testBucket;

    @TempDir
    private Path tempDir;
    
    @BeforeEach
    void setUp() throws Throwable {
        targetSyncFolder = new TemporarySyncFolder(tempDir);
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
        defaultFilters = Filters.getBuilder()
                .include(Filters.endsWith("txt"))
                .include(Filters.endsWith("bin"))
                .create();
    }
    
    @Test
    public void should_upload_incomplete_file() throws Exception {
        String fileName = "unfinished_writing_file.bin";
        targetSyncFolder.addFileFromResources(fileName);
        targetSyncFolder.lock(fileName);

        //synchronize folder
        Path directoryPath = targetSyncFolder.getFolderPath();
        Source directorySource = Source.getBuilder(directoryPath)
                .setFilters(defaultFilters)
                .setRecursive(true)
                .create();
        
        RemoteSync directorySync = new RemoteSync(directorySource, testBucket.getS3AsyncClient(), testBucket.getBucketName(), testBucket.getBucketPrefix());
        directorySync.run();

        //Check that the file still not exists on the server
        assertThat(testBucket.doesNameExists(fileName), is(false));

        //Check multipart upload exists
        MultipartUpload multipartUpload = testBucket.getMultipartUploadForName(fileName)
                .orElseThrow(() -> new AssertionError("Found no multipart upload for: "+fileName));

        //and the parts have the expected ETag
        Map<Integer, String> hashes = targetSyncFolder.getPartsHashes(fileName);
        for (Part partSummary : testBucket.getPartsForName(fileName, multipartUpload.uploadId())) {
            comparePart(partSummary, hashes);
        }
    }

    private void comparePart(Part part, Map<Integer, String> hashes) {
        int partNumber = part.partNumber();
        Assertions.assertEquals(hashes.get(partNumber), sanitizeETag(part.eTag()));
    }

    @Test
    public void should_record_listener_events_for_locked_multipart_upload() throws Exception {
        String fileName = "unfinished_writing_file.bin";
        targetSyncFolder.addFileFromResources(fileName);
        targetSyncFolder.lock(fileName);

        Source directorySource = Source.getBuilder(targetSyncFolder.getFolderPath())
                .setFilters(defaultFilters)
                .setRecursive(true)
                .create();

        RemoteSync directorySync = new RemoteSync(directorySource, testBucket.getS3AsyncClient(), testBucket.getBucketName(), testBucket.getBucketPrefix());
        RecordingProgressListener recordingListener = new RecordingProgressListener();
        directorySync.setListener(recordingListener);

        directorySync.run();

        String expectedEvents = String.join(System.lineSeparator(),
                "uploadFileStarted(alreadyUploadedBytes=0)",
                "uploadFileProgress(bytes=5242880)",
                "uploadFileProgress(bytes=10485760)"
        );

        Assertions.assertEquals(expectedEvents, recordingListener.render());
        Assertions.assertTrue(testBucket.getMultipartUploadForName(fileName).isPresent());
        Assertions.assertFalse(testBucket.doesNameExists(fileName));
    }

    @Test
    public void should_be_able_to_upload_failed_parts() throws Exception {
        String fileName = "unfinished_writing_file.bin";
        targetSyncFolder.addFileFromResources(fileName);
        targetSyncFolder.lock(fileName);

        String uploadId = testBucket.initiateMultipartUploadByName(fileName);
        
        //write third part of data
        targetSyncFolder.writeBytesToFile(fileName, PART_SIZE_IN_BYTES);

        //upload first and third part
        byte[] fileContent = Files.readAllBytes(Paths.get(targetSyncFolder.getFolderPath() + "/" + fileName));
        byte[] firstPart = new byte[PART_SIZE_IN_BYTES];
        byte[] thirdPart = new byte[PART_SIZE_IN_BYTES];
        System.arraycopy(fileContent, 0, firstPart, 0, PART_SIZE_IN_BYTES);
        System.arraycopy(fileContent, PART_SIZE_IN_BYTES * 2, thirdPart, 0, PART_SIZE_IN_BYTES);
        testBucket.uploadPartForName(fileName, uploadId, firstPart, 1);
        testBucket.uploadPartForName(fileName, uploadId, thirdPart, 3);

        //write additional data and delete lock file
        targetSyncFolder.writeBytesToFile(fileName, ONE_MEGABYTE);
        targetSyncFolder.unlock(fileName);

        //synchronize folder
        Path directoryPath = targetSyncFolder.getFolderPath();
        Source directorySource = Source.getBuilder(directoryPath)
                .setFilters(defaultFilters)
                .setRecursive(true)
                .create();
        
        RemoteSync directorySync = new RemoteSync(directorySource, testBucket.getS3AsyncClient(), testBucket.getBucketName(), testBucket.getBucketPrefix());
        directorySync.run();

        //Check that the file exists on the server
        assertThat(testBucket.doesNameExists(fileName), is(true));

        //Check that multipart upload completed and not exists anymore
        assertThat(testBucket.getMultipartUploadForName(fileName), is(Optional.empty()));

        //check complete file hash. ETag of complete file consists from complete file MD5 hash and parts count after "-" sign
        assertThat(sanitizeETag(testBucket.getObjectMetadataForName(fileName).eTag()), startsWith(targetSyncFolder.getCompleteFileMD5(fileName)));
    }



    @Test
    public void should_be_able_upload_empty_file_continue_incomplete_file_and_finalise() throws Exception {
        String fileName = "empty_file.bin";
        targetSyncFolder.addFileFromResources(fileName);
        targetSyncFolder.lock(fileName);

        //Upload empty file
        Path directoryPath = targetSyncFolder.getFolderPath();
        Source directorySource = Source.getBuilder(directoryPath)
                .setFilters(defaultFilters)
                .setRecursive(true)
                .create();
        
        RemoteSync directorySync = new RemoteSync(directorySource, testBucket.getS3AsyncClient(), testBucket.getBucketName(), testBucket.getBucketPrefix());
        directorySync.run();

        //Check multipart upload exists
        MultipartUpload multipartUpload = testBucket.getMultipartUploadForName(fileName)
                .orElseThrow(() -> new AssertionError("Found no multipart upload for: "+fileName));

        assertThat(testBucket.doesNameExists(fileName), is(false));
        List<Part> list1 = testBucket.getPartsForName(fileName, multipartUpload.uploadId());
        Assertions.assertNotNull(list1);
        Assertions.assertTrue(list1.isEmpty());
        
        targetSyncFolder.writeBytesToFile(fileName, PART_SIZE_IN_BYTES + ONE_MEGABYTE);
        
        //Upload incomplete file file
        directorySync.run();
        
        assertThat(testBucket.doesNameExists(fileName), is(false));
        List<Part> list2 = testBucket.getPartsForName(fileName, multipartUpload.uploadId());
        Assertions.assertNotNull(list2);
        Assertions.assertFalse(list2.isEmpty());
        
        targetSyncFolder.writeBytesToFile(fileName, 3 * PART_SIZE_IN_BYTES + ONE_MEGABYTE);
        targetSyncFolder.unlock(fileName);
        
        //Finalize
        directorySync.run();
        
        assertThat(testBucket.doesNameExists(fileName), is(true));
        
        assertThat(testBucket.getMultipartUploadForName(fileName), is(Optional.empty()));

        //check complete file hash. ETag of complete file consists from complete file MD5 hash and parts count after "-" sign
        assertThat(sanitizeETag(testBucket.getObjectMetadataForName(fileName).eTag()), startsWith(targetSyncFolder.getCompleteFileMD5(fileName)));
    }
}
