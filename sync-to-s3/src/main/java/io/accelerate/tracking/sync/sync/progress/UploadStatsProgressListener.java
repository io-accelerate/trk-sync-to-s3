package io.accelerate.tracking.sync.sync.progress;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class UploadStatsProgressListener implements ProgressListener {

    public static class FileUploadStat {

        private final double BYTE_PER_MILLISECOND_TO_MEGABYTES_PER_SECOND = 0.001;

        private final Clock clock;
        private final Instant startInstant;
        private long totalSize = 0;
        
        private final AtomicLong uploadedSize;

        FileUploadStat(Clock clock, long totalSize, long uploadedByte) {
            this.clock = clock;
            this.totalSize = totalSize;
            this.startInstant = clock.instant();
            this.uploadedSize = new AtomicLong(uploadedByte);
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getUploadedSize() {
            return uploadedSize.get();
        }

        void incrementUploadedSize(long size) {
            this.uploadedSize.getAndAdd(size);
        }

        public double getMBps() {
            long elapsedMilliseconds = Duration.between(startInstant, clock.instant()).toMillis();
            if (elapsedMilliseconds <= 0) {
                return 0;
            }
            double bytesUploaded = (double) this.uploadedSize.get();
            double bytePerMillisecond = bytesUploaded / elapsedMilliseconds;
            return bytePerMillisecond * BYTE_PER_MILLISECOND_TO_MEGABYTES_PER_SECOND;
        }

        public double getUploadRatio() {
            return (double) uploadedSize.get() / (double) totalSize;
        }
    }

    private FileUploadStat fileUploadStat = null;
    private final Clock clock;

    public UploadStatsProgressListener() {
        this(Clock.systemUTC());
    }

    public UploadStatsProgressListener(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void uploadFileStarted(File file, String uploadId, long uploadedByte) {
        fileUploadStat = new FileUploadStat(clock, file.length(), uploadedByte);
    }

    @Override
    public void uploadFileProgress(String uploadId, long uploadedByte) {
        fileUploadStat.incrementUploadedSize(uploadedByte);
    }

    @Override
    public void uploadFileFinished(File file) {
        fileUploadStat = null;
    }


    //~~~~ Getters


    public Optional<FileUploadStat> getCurrentStats() {
        //TODO Improve this class so that it can handle multiple uploads simultaneously
        return Optional.ofNullable(fileUploadStat);
    }


    public boolean isCurrentlyUploading() {
        return fileUploadStat != null;
    }
}
