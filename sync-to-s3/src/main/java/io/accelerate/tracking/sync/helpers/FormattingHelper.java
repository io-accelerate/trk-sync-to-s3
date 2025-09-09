package io.accelerate.tracking.sync.helpers;

public class FormattingHelper {
    
    public static String stripLeadingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.startsWith("/") ? s.substring(1) : s;
    }

    public static String sanitizeETag(String etag) {
        if (etag == null) return null;
        String s = etag.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static String buildKey(String prefix, String remoteFileName) {
        String p = prefix;
        if (p.isEmpty()) {
            return stripLeadingSlash(remoteFileName);
        }
        String normalizedPrefix = p.endsWith("/") ? p : p + "/";
        String name = stripLeadingSlash(remoteFileName);
        return normalizedPrefix + name;
    }
}
