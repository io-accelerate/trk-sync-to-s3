package io.accelerate.tracking.sync.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class Source {

    private static final Logger log = LoggerFactory.getLogger(Source.class);

    private Path path;

    private boolean isRecursive;

    private Filters filters;

    public static class Builder {

        private final Source source = new Source();

        public Builder(Path path) {
            source.path = path;
        }

        public Builder traverseDirectories(boolean traverse) {
            return this;
        }

        public Builder setRecursive(boolean isRecursive) {
            source.isRecursive = isRecursive;
            return this;
        }

        public Builder setFilters(Filters filters) {
            source.filters = filters;
            return this;
        }

        public Source create() {
            if (source.filters == null) {
                throw new RuntimeException("Cannot found filters.");
            }
            return source;
        }
    }

    public static Builder getBuilder(Path path) {
        return new Builder(path);
    }

    public Path getPath() {
        return path;
    }

    public Filters getFilters() {
        return filters;
    }

    public boolean isRecursive() {
        return isRecursive;
    }

    public boolean isValidPath() {
        File file = path.toFile();
        return file.isDirectory();
    }

    public List<String> getFilesToUpload() {
        try {
            int maxDepth = isRecursive ? Integer.MAX_VALUE : 1;
            File base = path.toFile();
            log.debug("Searching '{}' (recursive: {}) with filters [{}]", base.getAbsolutePath(), isRecursive, filters.describe());
            BiPredicate<Path, BasicFileAttributes> matcher = (filePath, fileAttr) -> {
                boolean accepted = fileAttr.isRegularFile() && filters.accept(filePath);
                if (log.isTraceEnabled()) {
                    log.trace("Evaluated '{}': {}", filePath, accepted ? "accepted" : "rejected");
                }
                return accepted;
            };
            List<String> matches = Files.find(path, maxDepth, matcher)
                    .map(filePath -> base.toURI().relativize(filePath.toFile().toURI()).getPath())
                    .collect(Collectors.toList());
            log.debug("Matched {} files: {}", matches.size(), matches);
            return matches;
        } catch (IOException ex) {
            log.error("Failed to discover files under '{}'", path, ex);
            return new ArrayList<>();
        }
    }
}
