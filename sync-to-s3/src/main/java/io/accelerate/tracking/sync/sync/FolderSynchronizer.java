package io.accelerate.tracking.sync.sync;

import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import io.accelerate.tracking.sync.upload.FileUploadingService;

import java.io.File;
import java.util.List;

class FolderSynchronizer {

    private final Source sourceFolder;

    private final FileUploadingService fileUploadingService;

    FolderSynchronizer(Source sourceFolder, FileUploadingService fileUploadingService) {
        this.sourceFolder = sourceFolder;
        this.fileUploadingService = fileUploadingService;
    }

    void synchronize() {
        List<String> paths = sourceFolder.getFilesToUpload();
        paths.forEach(filePath -> {
            File folder = sourceFolder.getPath().toFile();
            File uploadFile = new File(folder, filePath);
            fileUploadingService.upload(uploadFile, filePath);
        });
    }

    void setListener(ProgressListener listener) {
        fileUploadingService.setListener(listener);
    }
}
