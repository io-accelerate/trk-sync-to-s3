package io.accelerate.tracking.sync.sync;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Filters {

    private final List<Filter> includes = new ArrayList<>();

    private final List<Filter> excludes = new ArrayList<>();

    public static class Builder {

        private final Filters filters = new Filters();

        public Filters create() {
            if (filters.includes.isEmpty()) {
                throw new RuntimeException("No filters for inclusion found.");
            }
            return filters;
        }

        public final Builder exclude(Filter filter) {
            filters.exclude(filter);
            return this;
        }

        public final Builder include(Filter filter) {
            filters.include(filter);
            return this;
        }
    }

    private Filters() {
        exclude(getDefaultLockFilter());
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    public final void exclude(Filter filter) {
        excludes.add(filter);
    }

    public final void include(Filter filter) {
        includes.add(filter);
    }

    public boolean accept(Path path) {
        boolean accept = includes.stream()
                .anyMatch(e -> e.accept(path));

        boolean reject = excludes.stream()
                .anyMatch(e -> e.accept(path));

        return accept && !reject;
    }

    public String describe() {
        return "includes=" + includes.stream().map(Filter::description).collect(Collectors.toList())
                + ", excludes=" + excludes.stream().map(Filter::description).collect(Collectors.toList());
    }

    public static final Filter getDefaultLockFilter() {
        return new Filter() {
            @Override
            public boolean accept(Path path) {
                return path.getFileName().toString().endsWith(".lock");
            }

            @Override
            public String description() {
                return "endsWith(.lock)";
            }
        };
    }

    public static final Filter endsWith(String string) {
        return new Filter() {
            @Override
            public boolean accept(Path path) {
                return path.getFileName().toString().endsWith(string);
            }

            @Override
            public String description() {
                return "endsWith(" + string + ")";
            }
        };
    }

    public static final Filter startsWith(String string) {
        return new Filter() {
            @Override
            public boolean accept(Path path) {
                return path.getFileName().toString().startsWith(string);
            }

            @Override
            public String description() {
                return "startsWith(" + string + ")";
            }
        };
    }

    public static final Filter matches(String regex) {
        return new Filter() {
            @Override
            public boolean accept(Path path) {
                return path.getFileName().toString().matches(regex);
            }

            @Override
            public String description() {
                return "matches(" + regex + ")";
            }
        };
    }

    public static final Filter name(String name) {
        return new Filter() {
            @Override
            public boolean accept(Path path) {
                return path.getFileName().toString().equals(name);
            }

            @Override
            public String description() {
                return "name(" + name + ")";
            }
        };
    }
}
