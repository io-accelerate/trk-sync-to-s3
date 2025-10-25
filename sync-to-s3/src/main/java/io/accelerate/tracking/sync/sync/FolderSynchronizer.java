package io.accelerate.tracking.sync.sync;

import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import io.accelerate.tracking.sync.upload.FileUploadingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

class FolderSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(FolderSynchronizer.class);

    private final Source sourceFolder;

    private final FileUploadingService fileUploadingService;

    FolderSynchronizer(Source sourceFolder, FileUploadingService fileUploadingService) {
        this.sourceFolder = sourceFolder;
        this.fileUploadingService = fileUploadingService;
    }

    void synchronize() {
        log.debug("Scanning '{}' for files to upload", sourceFolder.getPath());
        List<String> paths = sourceFolder.getFilesToUpload();
        log.debug("Found {} files to upload", paths.size());
        paths.forEach(filePath -> {
            File folder = sourceFolder.getPath().toFile();
            File uploadFile = new File(folder, filePath);
            log.debug("Queueing '{}' for upload from '{}'", filePath, uploadFile.getAbsolutePath());
            fileUploadingService.upload(uploadFile, filePath);
        });
        if (paths.isEmpty()) {
            log.debug("No files found for upload in '{}'", sourceFolder.getPath());
        }
    }

    void setListener(ProgressListener listener) {
        log.debug("Setting listener on FolderSynchronizer to {}", listener != null ? listener.getClass().getSimpleName() : "null");
        fileUploadingService.setListener(listener);
    }
}
