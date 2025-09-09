package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.progress.DummyProgressListener;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUploadingService {

    private final UploadingStrategy uploadingStrategy;
    private final FileUploader fileUploader;
    

    public FileUploadingService(S3AsyncClient s3AsyncClient, String bucket, String prefix) {
        uploadingStrategy = new MultipartUploadingStrategy(s3AsyncClient, bucket, prefix);
        fileUploader = new FileUploaderImpl(uploadingStrategy);
    }
    
    public void setListener(ProgressListener listener) {
        uploadingStrategy.setListener(listener);
    }

    public void upload(File file) {
        upload(file, file.getName());
    }

    public void upload(File sourceFile, String remoteFileName) {
        try {
            fileUploader.upload(sourceFile, remoteFileName);
        } catch (UploadingException ex) {
            Logger.getLogger(FileUploadingService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
