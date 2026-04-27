package com.salat.gmediahud;

public class UpdateInfo {
    public String version;
    public String apkUrl;
    public String changelog;
    public long fileSize;

    public boolean isNewerThan(String currentVersion) {
        return compareVersions(version, currentVersion) > 0;
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.replace("v", "").split("\\.");
        String[] parts2 = v2.replace("v", "").split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }
}
