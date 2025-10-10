package io.accelerate.tracking.sync.sync.progress;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class UploadStatsProgressListener implements ProgressListener {

    public static class FileUploadStat {

        private static final double BYTES_PER_MILLISECOND_TO_MEGABYTES_PER_SECOND_MULTIPLIER = 0.001d;

        private final Clock clock;
        private final Instant uploadStartInstant;
        private final long totalBytes;
        private final AtomicLong uploadedBytes;

        FileUploadStat(Clock clock, long totalBytes, long alreadyUploadedBytes) {
            this.clock = clock;
            this.totalBytes = totalBytes;
            this.uploadStartInstant = clock.instant();
            this.uploadedBytes = new AtomicLong(alreadyUploadedBytes);
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getUploadedBytes() {
            return uploadedBytes.get();
        }

        void incrementUploadedBytes(long additionalUploadedBytes) {
            uploadedBytes.getAndAdd(additionalUploadedBytes);
        }

        public double getMegabytesPerSecond() {
            long elapsedTimeMillis = Duration.between(uploadStartInstant, clock.instant()).toMillis();
            if (elapsedTimeMillis <= 0) {
                return 0;
            }
            double totalUploadedBytes = (double) uploadedBytes.get();
            double bytesPerMillisecond = totalUploadedBytes / elapsedTimeMillis;
            return bytesPerMillisecond * BYTES_PER_MILLISECOND_TO_MEGABYTES_PER_SECOND_MULTIPLIER;
        }

        public double getUploadRatio() {
            return (double) uploadedBytes.get() / (double) totalBytes;
        }
    }

    private FileUploadStat activeUploadStat = null;
    private final Clock clock;

    public UploadStatsProgressListener() {
        this(Clock.systemUTC());
    }

    public UploadStatsProgressListener(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void uploadFileStarted(File file, String uploadId, long alreadyUploadedBytes) {
        activeUploadStat = new FileUploadStat(clock, file.length(), alreadyUploadedBytes);
    }

    @Override
    public void uploadFileProgress(String uploadId, long bytesUploadedSinceLastReport) {
        if (activeUploadStat != null) {
            activeUploadStat.incrementUploadedBytes(bytesUploadedSinceLastReport);
        }
    }

    @Override
    public void uploadFileFinished(File file) {
        activeUploadStat = null;
    }


    //~~~~ Getters


    public Optional<FileUploadStat> getCurrentStats() {
        //TODO Improve this class so that it can handle multiple uploads simultaneously
        return Optional.ofNullable(activeUploadStat);
    }


    public boolean isCurrentlyUploading() {
        return activeUploadStat != null;
    }
}
