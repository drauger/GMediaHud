package com.salat.gmediahud;  // Изменён package

import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.Manifest;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GisNotificationService extends NotificationListenerService {
    private static final String TAG = "GisNotificationService";
    private static final String GIS_PACKAGE = "ru.dublgis.dgismobile";
    private static final int MAX_ICONS = 50;
    private static final String ACTION_SHOW = "com.salat.gmediahud.SHOW";
    private static final String ACTION_HIDE = "com.salat.gmediahud.HIDE";
    private static final String TARGET_PACKAGE = "com.salat.gmediahud";

    private File iconsDir;
    private final Map<String, String> activeGisNotifications = new HashMap<>();
    private int notificationCounter = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        // Проверка разрешений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing READ_MEDIA_IMAGES permission");
            }
        }

        initIconsDirectory();
        activeGisNotifications.clear();
        notificationCounter = 0;
    }

    private void initIconsDirectory() {
        // Используем app-specific директорию для Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            iconsDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "2GIS_Icons");
        } else {
            iconsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "2GIS_Icons");
        }

        if (!iconsDir.exists()) {
            boolean created = iconsDir.mkdirs();
            Log.d(TAG, "Icons directory created: " + created);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected");
        activeGisNotifications.clear();
        notificationCounter = 0;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.getPackageName().equals(GIS_PACKAGE)) return;

        String key = sbn.getKey();
        String tag = "gis" + (++notificationCounter);
        activeGisNotifications.put(key, tag);

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString();
        String subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT, "").toString();
        String bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "").toString();

        // Разделяем по первому переносу строки
        String[] parts = text.split("\n", 2);
        String title = parts[0].trim();
        String subtitle = parts.length > 1 ? parts[1].trim() : " ";

        if (!subText.isEmpty()) {
            subtitle += " " + subText;
        }

        if (!bigText.isEmpty()) {
            subtitle += " " + bigText;
        }

        // Получаем изображение
        Bitmap picture = extras.getParcelable(Notification.EXTRA_PICTURE);
        Bitmap iconBitmap = null;

        if (picture != null) {
            iconBitmap = picture;
        } else {
            Icon largeIcon = notification.getLargeIcon();
            if (largeIcon != null) {
                Drawable drawable = largeIcon.loadDrawable(this);
                if (drawable != null) {
                    iconBitmap = drawableToBitmap(drawable);
                }
            }
        }

        // Получаем имя файла и сохраняем
        String iconFileName = getIconFileName(subtitle, iconBitmap);
        File iconFile = new File(iconsDir, iconFileName);

        if (!iconFile.exists() && iconBitmap != null && !iconBitmap.isRecycled()) {
            enforceMaxIconsLimit();
            saveBitmap(iconBitmap, iconFile);
        }

        // Отправляем broadcast в GMediaHUD
        sendShowCommand(title, subtitle, iconFile.getAbsolutePath(), tag);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!sbn.getPackageName().equals(GIS_PACKAGE)) return;

        String key = sbn.getKey();
        String tag = activeGisNotifications.remove(key);

        if (tag != null) {
            sendHideCommand(tag);
        }
    }

    private void sendShowCommand(String title, String subtitle, String iconPath, String tag) {
        Intent intent = new Intent(ACTION_SHOW);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("title", title);
        intent.putExtra("subtitle", subtitle);
        intent.putExtra("art", iconPath);
        intent.putExtra("duration", 100);
        intent.putExtra("params", "queue=0,warning=0,source=6,toast=0,tag=" + tag);

        sendBroadcast(intent);
        Log.d(TAG, "SHOW: " + title + " | tag=" + tag);
    }

    private void sendHideCommand(String tag) {
        Intent intent = new Intent(ACTION_HIDE);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("tag", tag);
        sendBroadcast(intent);
        Log.d(TAG, "HIDE: tag=" + tag);
    }

    // ============ Вспомогательные методы ============

    private String getIconFileName(String text, Bitmap bitmap) {
        String baseName = getManeuverName(text);

        if (!"nav".equals(baseName)) {
            return baseName + ".png";
        }

        if (bitmap == null) {
            return "nav_default.png";
        }

        String bitmapHash = getBitmapHash(bitmap);
        return "nav_" + bitmapHash + ".png";
    }

    private String getBitmapHash(Bitmap bitmap) {
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, false);
            ByteBuffer buffer = ByteBuffer.allocate(scaled.getByteCount());
            scaled.copyPixelsToBuffer(buffer);
            byte[] pixels = buffer.array();

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(pixels);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }

            if (scaled != bitmap) {
                scaled.recycle();
            }

            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Hash error", e);
            return String.valueOf(System.currentTimeMillis() % 10000);
        }
    }

    private String getManeuverName(String text) {
        String t = text.toLowerCase();
        if (t.contains("налево")) return "left";
        if (t.contains("направо")) return "right";
        if (t.contains("разворот")) return "uturn";
        if (t.contains("круг") || t.contains("кольцо")) return "roundabout";
        if (t.contains("съезд")) return "exit";
        if (t.contains("въезд")) return "enter";
        if (t.contains("прямо")) return "straight";
        if (t.contains("слияние")) return "merge";
        if (t.contains("пешеход")) return "pedestrian";
        if (t.contains("парковка")) return "parking";
        return "nav";
    }

    private void enforceMaxIconsLimit() {
        File[] files = iconsDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length < MAX_ICONS) return;

        Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

        int deleteCount = files.length - (MAX_ICONS - 1);
        for (int i = 0; i < deleteCount; i++) {
            if (files[i].delete()) {
                Log.d(TAG, "Deleted old icon: " + files[i].getName());
            }
        }
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.d(TAG, "Saved icon: " + file.getName());
        } catch (Exception e) {
            Log.e(TAG, "Save error", e);
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }
}