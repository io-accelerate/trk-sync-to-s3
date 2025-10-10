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
    private static final NumberFormat PERCENTAGE_FORMATTER = NumberFormat.getPercentInstance(DISPLAY_LOCALE);
    private static final NumberFormat SIZE_FORMATTER = NumberFormat.getNumberInstance(DISPLAY_LOCALE);
    private static final NumberFormat SPEED_FORMATTER = NumberFormat.getNumberInstance(DISPLAY_LOCALE);

    static {
        PERCENTAGE_FORMATTER.setMinimumFractionDigits(1);
        SIZE_FORMATTER.setMinimumFractionDigits(1);
        SPEED_FORMATTER.setMinimumFractionDigits(1);
    }

    @BeforeEach
    public void setUp() {
        clock = new MutableClock();
        listener = new UploadStatsProgressListener(clock);
        file = mock(File.class);
        when(file.length()).thenReturn(Long.valueOf(1000000));
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
        assertEquals(0.0, stat.getMBps(), 0.1);
        clock.advanceMillis(1000);
        stat.incrementUploadedSize(500000);
        assertEquals(0.5, stat.getMBps(), 0.01);
    }

    @Test
    public void upload() {
        listener.uploadFileStarted(file, "upload", 0);
        UploadStatsProgressListener.FileUploadStat stat = listener.getCurrentStats().get();
        assertEquals(1000000, stat.getTotalSize());
        assertEquals(0, stat.getUploadedSize());
        assertEquals("Uploaded 0.0% of 0.954 MB at   0.0 MB/sec", renderMetrics(stat));

        clock.advanceMillis(2000);
        listener.uploadFileProgress("upload", 500000);
        assertEquals(500000, stat.getUploadedSize());
        assertEquals(0.5, stat.getUploadRatio(), 0.001);
        assertEquals("Uploaded 50.0% of 0.954 MB at  0.25 MB/sec", renderMetrics(stat));
        listener.uploadFileFinished(file);
        assertFalse(listener.isCurrentlyUploading());
        assertTrue(listener.getCurrentStats().isEmpty());
    }

    @Test
    public void resumedUploadsShouldExposeExistingProgress() {
        long alreadyUploadedBytes = 250000;

        listener.uploadFileStarted(file, "upload", alreadyUploadedBytes);

        UploadStatsProgressListener.FileUploadStat stat = listener.getCurrentStats().get();
        assertEquals(1000000, stat.getTotalSize());
        assertEquals(alreadyUploadedBytes, stat.getUploadedSize());
        assertEquals(0.25, stat.getUploadRatio(), 0.001);
        assertEquals("Uploaded 25.0% of 0.954 MB at   0.0 MB/sec", renderMetrics(stat));

        clock.advanceMillis(2000);
        listener.uploadFileProgress("upload", 250000);

        assertEquals(500000, stat.getUploadedSize());
        assertEquals(0.5, stat.getUploadRatio(), 0.001);
        assertEquals("Uploaded 50.0% of 0.954 MB at  0.25 MB/sec", renderMetrics(stat));
    }

    @Test
    public void completedUploadsShouldClearCurrentStats() {
        listener.uploadFileStarted(file, "upload", 0);
        assertTrue(listener.getCurrentStats().isPresent());

        listener.uploadFileFinished(file);

        assertTrue(listener.getCurrentStats().isEmpty());
        assertFalse(listener.isCurrentlyUploading());
    }

    private String renderMetrics(UploadStatsProgressListener.FileUploadStat stat) {
        return String.format(DISPLAY_LOCALE,
                "Uploaded %3s of %3s MB at %5s MB/sec",
                PERCENTAGE_FORMATTER.format(stat.getUploadRatio()),
                SIZE_FORMATTER.format(bytesToMegabytes(stat.getTotalSize())),
                SPEED_FORMATTER.format(stat.getMBps()));
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
