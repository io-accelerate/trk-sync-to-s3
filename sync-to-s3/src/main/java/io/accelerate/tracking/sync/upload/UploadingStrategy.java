package io.accelerate.tracking.sync.upload;


import io.accelerate.tracking.sync.sync.SyncException;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;

import java.io.File;
import java.io.IOException;

public interface UploadingStrategy {
    
    void upload(File sourceFile, String remoteName) throws SyncException, IOException;

    void setListener(ProgressListener listener);
}
