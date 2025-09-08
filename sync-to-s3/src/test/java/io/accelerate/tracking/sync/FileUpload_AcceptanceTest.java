package io.accelerate.tracking.sync;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.sync.Filters;
import io.accelerate.tracking.sync.sync.RemoteSync;
import io.accelerate.tracking.sync.sync.Source;
import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;

public class FileUpload_AcceptanceTest {

    public LocalTestBucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
    }

    @Test
    public void should_not_upload_file_if_already_present() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_1/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("txt")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        //Upload first file just to check in test that it will not be uploaded twice
        RemoteSync sync = new RemoteSync(source, testBucket.asDestination());
        sync.run();

        // Sleep 2 seconds to distinguish that file uploaded_once.txt on aws was not uploaded by next call
        Thread.sleep(2000);
        Instant uploadingTime = Instant.now();
        sync.run();

        HeadObjectResponse objectMetadata = testBucket.getObjectMetadata("already_uploaded.txt");
        Instant actualLastModifiedDate = objectMetadata.lastModified();

        //Check that file is older than last uploading start
        Assertions.assertTrue(actualLastModifiedDate.isBefore(uploadingTime));
    }

    @Test
    public void should_upload_simple_file_to_bucket() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_2/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("txt")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        RemoteSync sync = new RemoteSync(source, testBucket.asDestination());
        sync.run();

        MatcherAssert.assertThat(testBucket.doesObjectExists("sample_small_file_to_upload.txt"), is(true));
    }

    @Test
    public void should_upload_large_file_to_bucket_using_multipart_upload() throws Exception {
        Path path = Paths.get("src/test/resources/test_a_3/");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("bin")).create();
        Source source = Source.getBuilder(path)
                .setFilters(filters)
                .create();

        RemoteSync sync = new RemoteSync(source, testBucket.asDestination());
        sync.run();

        MatcherAssert.assertThat(testBucket.doesObjectExists("large_file.bin"), is(true));
    }

}
