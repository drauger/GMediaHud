package com.salat.gmediahud;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String GITHUB_API = "https://api.github.com/repos/{OWNER}/{REPO}/releases/latest";
    private static final String TAG = "UpdateChecker";

    public interface UpdateCallback {
        void onUpdateAvailable(UpdateInfo info);
        void onNoUpdate();
        void onError(String error);
    }

    public static void checkForUpdate(Context context, String owner, String repo, UpdateCallback callback) {
        String url = GITHUB_API.replace("{OWNER}", owner).replace("{REPO}", repo);
        String currentVersion = getAppVersion(context);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    postError(callback, "HTTP " + responseCode);
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject release = new JSONObject(response.toString());
                String latestVersion = release.getString("tag_name");

                JSONArray assets = release.getJSONArray("assets");
                String apkUrl = null;
                long fileSize = 0;

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.getString("name");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url");
                        fileSize = asset.getLong("size");
                        break;
                    }
                }

                UpdateInfo info = new UpdateInfo();
                info.version = latestVersion;
                info.apkUrl = apkUrl;
                info.changelog = release.optString("body", "");
                info.fileSize = fileSize;

                if (info.isNewerThan(currentVersion) && apkUrl != null) {
                    postUpdateAvailable(callback, info);
                } else {
                    postNoUpdate(callback);
                }

            } catch (Exception e) {
                Log.e(TAG, "Check failed", e);
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void postUpdateAvailable(UpdateCallback callback, UpdateInfo info) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onUpdateAvailable(info));
    }

    private static void postNoUpdate(UpdateCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onNoUpdate());
    }

    private static void postError(UpdateCallback callback, String error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }

    private static String getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }
}