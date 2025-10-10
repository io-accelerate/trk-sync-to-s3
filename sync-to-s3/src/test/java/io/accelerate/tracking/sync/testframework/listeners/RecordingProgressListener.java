package io.accelerate.tracking.sync.testframework.listeners;

import io.accelerate.tracking.sync.sync.progress.ProgressListener;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only progress listener that records every callback.
 */
public final class RecordingProgressListener implements ProgressListener {

    private final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void uploadFileStarted(File file, String uploadId, long uploadedByte) {
        events.add("uploadFileStarted(alreadyUploadedBytes=" + uploadedByte + ")");
    }

    @Override
    public void uploadFileProgress(String uploadId, long uploadedByte) {
        events.add("uploadFileProgress(bytes=" + uploadedByte + ")");
    }

    @Override
    public void uploadFileFinished(File file) {
        events.add("uploadFileFinished()");
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public String render() {
        return String.join(System.lineSeparator(), events);
    }
}
