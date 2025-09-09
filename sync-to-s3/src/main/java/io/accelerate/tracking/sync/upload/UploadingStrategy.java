package io.accelerate.tracking.sync.upload;


import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;

import java.io.File;
import java.io.IOException;

public interface UploadingStrategy {
    
    void upload(File sourceFile, String remoteName) throws DestinationOperationException, IOException;

    void setListener(ProgressListener listener);
}
