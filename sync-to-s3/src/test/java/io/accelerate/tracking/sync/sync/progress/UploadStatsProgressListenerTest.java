package io.accelerate.tracking.sync.sync.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UploadStatsProgressListenerTest {

    private UploadStatsProgressListener listener;
    private File file;
    private MutableClock clock;

    private static final Locale DISPLAY_LOCALE = Locale.US;
    private static final double BYTES_PER_MEGABYTE = 1024d * 1024d;
    private static final long FILE_SIZE_BYTES = Math.round(BYTES_PER_MEGABYTE * 0.10);
    private static final NumberFormat PERCENTAGE_FORMATTER = NumberFormat.getPercentInstance(DISPLAY_LOCALE);
    private static final NumberFormat SIZE_FORMATTER = NumberFormat.getNumberInstance(DISPLAY_LOCALE);
    private static final NumberFormat SPEED_FORMATTER = NumberFormat.getNumberInstance(DISPLAY_LOCALE);

    static {
        PERCENTAGE_FORMATTER.setMinimumFractionDigits(1);
        SIZE_FORMATTER.setMinimumFractionDigits(2);
        SIZE_FORMATTER.setMaximumFractionDigits(2);
        SPEED_FORMATTER.setMinimumFractionDigits(2);
        SPEED_FORMATTER.setMaximumFractionDigits(2);
    }

    @BeforeEach
    public void setUp() {
        clock = new MutableClock();
        listener = new UploadStatsProgressListener(clock);
        file = mock(File.class);
        when(file.length()).thenReturn(FILE_SIZE_BYTES);
    }

    @Test
    public void isCurrentlyUploadingShouldReturnFalse() {
        assertFalse(listener.isCurrentlyUploading());
    }

    @Test
    public void getCurrentStatsShouldBeEmptyWhenNoUploadInProgress() {
        assertTrue(listener.getCurrentStats().isEmpty());
    }

    @Test
    public void isCurrentlyUploadingShouldReturnTrue() {
        listener.uploadFileStarted(file, "upload", 0);
        assertTrue(listener.isCurrentlyUploading());
    }

    @Test
    public void handleTimestampZeroFileUploadStat() throws InterruptedException {
        listener.uploadFileStarted(file, "upload", 0);
        UploadStatsProgressListener.FileUploadStat stat = listener.getCurrentStats().get();
        assertEquals("Uploaded 0.0% of 0.10 MB at  0.00 MB/sec", renderMetrics(stat));
        clock.advanceMillis(1000);
        stat.incrementUploadedBytes(FILE_SIZE_BYTES / 2);
        assertEquals("Uploaded 50.0% of 0.10 MB at  0.05 MB/sec", renderMetrics(stat));
    }

    @Test
    public void upload() {
        listener.uploadFileStarted(file, "upload", 0);
        UploadStatsProgressListener.FileUploadStat stat = listener.getCurrentStats().get();
        assertEquals("Uploaded 0.0% of 0.10 MB at  0.00 MB/sec", renderMetrics(stat));

        clock.advanceMillis(500);
        listener.uploadFileProgress("upload", FILE_SIZE_BYTES / 2);
        assertEquals("Uploaded 50.0% of 0.10 MB at  0.10 MB/sec", renderMetrics(stat));
        listener.uploadFileFinished(file);
        assertFalse(listener.isCurrentlyUploading());
        assertTrue(listener.getCurrentStats().isEmpty());
    }

    @Test
    public void resumedUploadsShouldExposeExistingProgress() {
        long alreadyUploadedBytes = FILE_SIZE_BYTES / 4;

        listener.uploadFileStarted(file, "upload", alreadyUploadedBytes);

        UploadStatsProgressListener.FileUploadStat stat = listener.getCurrentStats().get();
        assertEquals("Uploaded 25.0% of 0.10 MB at  0.00 MB/sec", renderMetrics(stat));

        clock.advanceMillis(250);
        listener.uploadFileProgress("upload", FILE_SIZE_BYTES / 4);

        assertEquals("Uploaded 50.0% of 0.10 MB at  0.21 MB/sec", renderMetrics(stat));
    }

    @Test
    public void completedUploadsShouldClearCurrentStats() {
        listener.uploadFileStarted(file, "upload", 0);
        assertTrue(listener.getCurrentStats().isPresent());

        listener.uploadFileFinished(file);

        assertTrue(listener.getCurrentStats().isEmpty());
        assertFalse(listener.isCurrentlyUploading());
    }
    
    
    // ~~~~~~~~~ Helper scripts ~~~~~~~~~

    private String renderMetrics(UploadStatsProgressListener.FileUploadStat stat) {
        return String.format(DISPLAY_LOCALE,
                "Uploaded %3s of %3s MB at %5s MB/sec",
                PERCENTAGE_FORMATTER.format(stat.getUploadRatio()),
                SIZE_FORMATTER.format(bytesToMegabytes(stat.getTotalBytes())),
                SPEED_FORMATTER.format(stat.getMegabytesPerSecond()));
    }

    private double bytesToMegabytes(long bytes) {
        return bytes / BYTES_PER_MEGABYTE;
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant = Instant.parse("2024-01-01T00:00:00Z");
        private ZoneId zoneId = ZoneId.of("UTC");

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock clock = new MutableClock();
            clock.currentInstant = this.currentInstant;
            clock.zoneId = zone;
            return clock;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        void advanceMillis(long millis) {
            currentInstant = currentInstant.plusMillis(millis);
        }
    }
}
