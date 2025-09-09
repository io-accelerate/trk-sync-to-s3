package io.accelerate.tracking.sync.sync;

import io.accelerate.tracking.sync.sync.progress.DummyProgressListener;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import io.accelerate.tracking.sync.upload.FileUploadingService;
import software.amazon.awssdk.services.s3.S3AsyncClient;


public class RemoteSync {

    private final Source sourceFolder;
    private final S3AsyncClient s3AsyncClient;
    private final String bucket;
    private final String prefix;

    private ProgressListener listener;

    public RemoteSync(Source sourceFolder, S3AsyncClient s3AsyncClient, String bucket, String prefix) {
        this.sourceFolder = sourceFolder;
        this.s3AsyncClient = s3AsyncClient;
        this.bucket = bucket;
        this.prefix = prefix;
        
        if (!this.sourceFolder.isValidPath()) {
            throw new RuntimeException("Source has to be a directory");
        }
        
        this.listener = new DummyProgressListener();
    }

    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    public void run() {
        FileUploadingService fileUploadingService = new FileUploadingService(s3AsyncClient, bucket, prefix);
        FolderSynchronizer folderSynchronizer = new FolderSynchronizer(sourceFolder, fileUploadingService);
        folderSynchronizer.setListener(listener);
        folderSynchronizer.synchronize();
    }

}
