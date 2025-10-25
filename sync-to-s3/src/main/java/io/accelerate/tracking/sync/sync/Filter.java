package io.accelerate.tracking.sync.sync;

import java.nio.file.Path;

public interface Filter {

    public boolean accept(Path path);

    default String description() {
        return getClass().getName();
    }
}
