package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.File;

public class FileUploadingService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadingService.class);

    private final UploadingStrategy uploadingStrategy;
    private final FileUploader fileUploader;


    public FileUploadingService(S3AsyncClient s3AsyncClient, String bucket, String prefix) {
        log.debug("Creating FileUploadingService for bucket '{}' and prefix '{}'", bucket, prefix);
        uploadingStrategy = new MultipartUploadingStrategy(s3AsyncClient, bucket, prefix);
        fileUploader = new FileUploaderImpl(uploadingStrategy);
    }

    public void setListener(ProgressListener listener) {
        log.debug("Setting upload progress listener to {}", listener != null ? listener.getClass().getSimpleName() : "null");
        uploadingStrategy.setListener(listener);
    }

    public void upload(File file) {
        upload(file, file.getName());
    }

    public void upload(File sourceFile, String remoteFileName) {
        log.debug("Uploading '{}' as '{}'", sourceFile == null ? "null" : sourceFile.getAbsolutePath(), remoteFileName);
        try {
            fileUploader.upload(sourceFile, remoteFileName);
            log.debug("Completed upload for '{}'", remoteFileName);
        } catch (UploadingException ex) {
            log.error("Failed to upload '{}' as '{}'", sourceFile == null ? "null" : sourceFile.getAbsolutePath(), remoteFileName, ex);
        }
    }
}
