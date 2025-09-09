package io.accelerate.tracking.sync;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.accelerate.tracking.sync.sync.Filters;
import io.accelerate.tracking.sync.sync.RemoteSync;
import io.accelerate.tracking.sync.sync.Source;
import io.accelerate.tracking.sync.testframework.rules.LocalTestBucket;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.is;

public class FolderSync_AcceptanceTest {

    public LocalTestBucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new LocalTestBucket();
        testBucket.beforeEach();
    }

    @Test
    public void should_upload_all_new_files_from_folder() throws Exception {
        //state before first upload
        Path filePath = Paths.get("src/test/resources/test_dir/test_file_1.txt");
        testBucket.upload("test_file_1.txt", filePath);

        MatcherAssert.assertThat(testBucket.doesObjectExists("test_file_1.txt"), is(true));
        MatcherAssert.assertThat(testBucket.doesObjectExists("test_file_2.txt"), is(false));
        MatcherAssert.assertThat(testBucket.doesObjectExists("subdir/sub_test_file_1.txt"), is(false));

        //synchronize folder
        Path directoryPath = Paths.get("src/test/resources/test_dir");
        Filters filters = Filters.getBuilder().include(Filters.endsWith("txt")).create();
        Source directorySource = Source.getBuilder(directoryPath)
                .setFilters(filters)
                .setRecursive(true)
                .create();

        RemoteSync directorySync = new RemoteSync(directorySource, testBucket.getS3AsyncClient(), testBucket.getBucketName(), testBucket.getBucketPrefix());
        directorySync.run();

        //state after sync
        MatcherAssert.assertThat(testBucket.doesObjectExists("test_file_1.txt"), is(true));
        MatcherAssert.assertThat(testBucket.doesObjectExists("test_file_2.txt"), is(true));
        MatcherAssert.assertThat(testBucket.doesObjectExists("subdir/sub_test_file_1.txt"), is(true));
    }

}
