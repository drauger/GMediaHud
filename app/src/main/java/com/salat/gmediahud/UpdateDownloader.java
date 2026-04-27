package com.salat.gmediahud;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateDownloader {
    private static final String TAG = "UpdateDownloader";

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(File apkFile);
        void onError(String error);
    }

    public static void download(Context context, UpdateInfo info, DownloadCallback callback) {
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            downloadDir = context.getFilesDir();
        }
        File apkFile = new File(downloadDir, "update.apk");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(info.apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int totalSize = conn.getContentLength();
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                int downloaded = 0;
                int read;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    downloaded += read;

                    if (totalSize > 0) {
                        int percent = (downloaded * 100) / totalSize;
                        postProgress(callback, percent);
                    }
                }

                fos.flush();
                fos.close();
                is.close();

                postComplete(callback, apkFile);

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public static void installApk(Context context, File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    private static void postProgress(DownloadCallback callback, int percent) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(percent));
    }

    private static void postComplete(DownloadCallback callback, File apkFile) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(apkFile));
    }

    private static void postError(DownloadCallback callback, String error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }
}