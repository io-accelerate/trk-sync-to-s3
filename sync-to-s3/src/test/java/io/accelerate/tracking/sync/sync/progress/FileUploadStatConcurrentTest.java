package io.accelerate.tracking.sync.sync.progress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;

public class FileUploadStatConcurrentTest {

    class CounterRunnable implements Runnable {

        private UploadStatsProgressListener.FileUploadStat stat;

        public CounterRunnable(UploadStatsProgressListener.FileUploadStat stat) {
            this.stat = stat;
        }

        public void run() {
            for (int i = 0; i < 1000000; i++) {
                stat.incrementUploadedBytes(1);
            }
        }
    }

    @Test
    public void incrementUploadSizeInRaceCondition() throws InterruptedException {
        long total = 1000000 * 2;
        UploadStatsProgressListener.FileUploadStat stat = new UploadStatsProgressListener.FileUploadStat(Clock.systemUTC(), total, 0);

        Thread thread1 = new Thread(new CounterRunnable(stat));
        thread1.setName("add thread");
        thread1.start();

        Thread thread2 = new Thread(new CounterRunnable(stat));
        thread2.setName("add thread2");
        thread2.start();

        thread1.join();
        thread2.join();

        Assertions.assertEquals(stat.getUploadedBytes(), total);
        Assertions.assertEquals((double) 1, stat.getUploadRatio(), 0.00001);
    }
}
