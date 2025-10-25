package io.accelerate.tracking.sync;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import io.accelerate.tracking.sync.credentials.AWSSecretProperties;
import io.accelerate.tracking.sync.sync.Filters;
import io.accelerate.tracking.sync.sync.RemoteSync;
import io.accelerate.tracking.sync.sync.Source;
import io.accelerate.tracking.sync.sync.SyncException;
import io.accelerate.tracking.sync.sync.progress.UploadStatsProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Timer;
import java.util.TimerTask;

@Parameters
public class SyncFileApp {

    @Parameter(names = {"--config", "-c"}, required = true)
    private String configPath;

    @Parameter(names = {"--dir", "-d"}, required = true)
    private String dirPath;

    @Parameter(names = {"--recursive", "-R"})
    private boolean recursive = false;

    @Parameter(names = {"--filter-extension"})
    private String filterExtension = "txt";

    private static final Logger log = LoggerFactory.getLogger(SyncFileApp.class);
    private static final NumberFormat percentageFormatter = NumberFormat.getPercentInstance();
    private static final NumberFormat uploadSpeedFormatter = NumberFormat.getNumberInstance();

    static {
        percentageFormatter.setMinimumFractionDigits(1);
        uploadSpeedFormatter.setMinimumFractionDigits(1);
    }

    public static void main(String[] args) throws SyncException {
        SyncFileApp app = new SyncFileApp();
        JCommander jCommander = new JCommander(app);
        try {
            jCommander.parse(args);
        } catch (ParameterException e) {
            log.error("Invalid command line arguments: {}", e.getMessage());
            jCommander.usage();
            throw e;
        }

        log.info("Starting sync from '{}' (recursive: {}) using config '{}' with extension filter '{}'",
                app.dirPath,
                app.recursive,
                app.configPath,
                app.filterExtension);

        try {
            app.run();
            log.info("Sync completed successfully");
        } catch (SyncException e) {
            log.error("Sync failed", e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Sync failed with an unexpected error", e);
            throw e;
        }
    }

    private void run() throws SyncException {
        // Prepare
        Source source = buildSource();
        Path path = Paths.get(configPath);
        log.info("Loading AWS credentials from '{}'", path.toAbsolutePath());

        AWSSecretProperties awsSecretProperties;
        try {
            awsSecretProperties = AWSSecretProperties.fromPlainTextFile(path);
        } catch (RuntimeException e) {
            log.error("Failed to load AWS credentials from '{}'", path.toAbsolutePath(), e);
            throw e;
        }

        String bucket = awsSecretProperties.getS3Bucket();
        String prefix = awsSecretProperties.getS3Prefix();
        log.info("Target bucket '{}' with prefix '{}'", bucket, prefix == null ? "" : prefix);

        S3AsyncClient s3Client;
        try {
            s3Client = awsSecretProperties.createClient();
        } catch (RuntimeException e) {
            log.error("Failed to create S3 client for bucket '{}'", bucket, e);
            throw e;
        }

        RemoteSync sync = new RemoteSync(source, s3Client, bucket, prefix);

        // Configure progress listener
        UploadStatsProgressListener uploadStatsProgressListener = new UploadStatsProgressListener();
        sync.setListener(uploadStatsProgressListener);
        Timer timer = new Timer();


        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                uploadStatsProgressListener.getCurrentStats().ifPresent(fileUploadStat -> System.out.println("\rUploaded : "
                        + percentageFormatter.format(fileUploadStat.getUploadRatio())
                        + ". "
                        + fileUploadStat.getUploadedBytes() + "/" + fileUploadStat.getTotalBytes()
                        + " bytes. "
                        + uploadSpeedFormatter.format(fileUploadStat.getMegabytesPerSecond())
                        + " Mbps"));
            }
        }, 0, 1000);

        try {
            log.info("Beginning sync run");
            sync.run();
            log.info("Sync execution finished");
        } finally {
            timer.cancel();
        }
    }

    private Source buildSource() {
        Filters filters = Filters.getBuilder()
                .include(Filters.endsWith("." + filterExtension))
                .create();
        log.debug("Applying extension filter '.{}'", filterExtension);
        return Source.getBuilder(Paths.get(dirPath))
                .setFilters(filters)
                .setRecursive(recursive)
                .create();
    }

}
