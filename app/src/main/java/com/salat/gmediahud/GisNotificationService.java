package com.salat.gmediahud;  // Изменён package

import android.app.AlertDialog;
import android.app.Notification;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GisNotificationService extends NotificationListenerService {
//    private static final String TAG = "GisNotificationService";
    private static final String PREFS_NAME = "GisServicePrefs";
    private static final String KEY_ENABLED = "gis_enabled";
    private static final String KEY_SOUND = "gis_sound_enabled";
    private static final String KEY_LOGS = "gis_logs_enabled";

    private static final String PACKAGE_2GIS = "ru.dublgis.dgismobile";
    private static final String PACKAGE_YANDEX_MAPS = "ru.yandex.yandexmaps";
    private static final String PACKAGE_YANDEX_NAVIGATOR = "ru.yandex.yandexnavi";
    private static final String PACKAGE_ANTIRADAR = "air.StrelkaSDFREE";
    private static final String PACKAGE_HUDFREE = "air.StrelkaHUDFREE";
    private static final String PACKAGE_HUDPREMIUM = "air.StrelkaHUDPREMIUM";

    private static final Set<String> NAV_PACKAGES = new HashSet<>();
    static {
        NAV_PACKAGES.add(PACKAGE_2GIS);
        NAV_PACKAGES.add(PACKAGE_YANDEX_MAPS);
        NAV_PACKAGES.add(PACKAGE_YANDEX_NAVIGATOR);
        NAV_PACKAGES.add(PACKAGE_ANTIRADAR);
        NAV_PACKAGES.add(PACKAGE_HUDFREE);
        NAV_PACKAGES.add(PACKAGE_HUDPREMIUM);
    }

    private static final int MAX_ICONS = 100;
    private static final String ACTION_SHOW = "com.salat.gmediahud.SHOW";
    private static final String ACTION_HIDE = "com.salat.gmediahud.HIDE";
    private static final String TARGET_PACKAGE = "com.salat.gmediahud";

    private File gisDir;
    private final Map<String, String> activeGisNotifications = new HashMap<>();
    private int notificationCounter = 0;
    private String lastNotification;
    private String lastTarget;

    @Override
    public void onCreate() {
        super.onCreate();

        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + getPackageName())));
        }

        // Проверка при старте
        UpdateChecker.checkForUpdate(this, "drauger", "GMediaHud", new UpdateChecker.UpdateCallback() {
            @Override
            public void onUpdateAvailable(UpdateInfo info) {
                showUpdateDialog(info);
            }

            @Override
            public void onNoUpdate() {
//                Log.d("Main", "No update available");
            }

            @Override
            public void onError(String error) {
//                Log.e("Main", "Update check error: " + error);
            }
        });

        initDirectory();
        activeGisNotifications.clear();
        notificationCounter = 0;
    }

    private void initDirectory() {
        gisDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GIS_Icons");

        if (!gisDir.exists()) {
            if (!gisDir.mkdirs())
                Toast.makeText(this, "Не удалось создать рабочую папку", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
//        Log.d(TAG, "Notification listener connected");
        activeGisNotifications.clear();
        notificationCounter = 0;
    }

    private boolean isGisEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isGisEnabled()) return;
        if (!NAV_PACKAGES.contains(sbn.getPackageName())) return;

        String packageName = sbn.getPackageName();

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString();
        String subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT, "").toString();
        String bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "").toString();

        String target = " ";
        int distance = 0;

        String title = " ";
        String subtitle = " ";

        switch (packageName) {
            case PACKAGE_2GIS:
                // 2GIS: text содержит манёвр и расстояние через \n
                String[] parts = text.split("\n", 2);
                title = parts[0].trim();

                parts = title.split("\\s*?[-\\u2013\\u2014]\\s*?", 2);
                subtitle = parts[0].trim();
                target = parts.length > 1 ? parts[1].trim() : " ";

                distance = Integer.parseInt((subtitle.split(" ", 2))[0].trim());
                break;
            case PACKAGE_YANDEX_MAPS:
            case PACKAGE_YANDEX_NAVIGATOR:
            case PACKAGE_ANTIRADAR:
            case PACKAGE_HUDFREE:
            case PACKAGE_HUDPREMIUM:
                // Яндекс: обычно title = манёвр, text = расстояние/улица
                title = extras.getCharSequence(Notification.EXTRA_TITLE, "").toString();
                if (title.isEmpty()) {
                    title = text.split("\n")[0].trim();
                }
                subtitle = text;

                if (!subText.isEmpty()) {
                    subtitle += " " + subText;
                }

                if (!bigText.isEmpty()) {
                    subtitle += " " + bigText;
                }
                break;
        }

//        if (PACKAGE_2GIS.equals(packageName)) {
//        } else {
//        }

        if (title.equals(lastNotification) || distance > 500) return;

        lastNotification = title;
        title = target;

        int warning = 0;

        if (target != lastTarget) {
            warning = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SOUND, false) ? 1 : 0;
            lastTarget = target;
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
        String iconFileName = getIconFileName(title, iconBitmap);
        File iconFile = new File(gisDir, iconFileName);

        if (!iconFile.exists() && iconBitmap != null && !iconBitmap.isRecycled()) {
            enforceMaxIconsLimit();
            saveBitmap(iconBitmap, iconFile);
        }

        String key = sbn.getKey();
        String tag = "gis" + (++notificationCounter);
        activeGisNotifications.put(key, tag);

        String params = "queue=0,warning=";
        params += warning;
        params += ",source=6,toast=0,tag=";
        params += tag;

        // Отправляем broadcast в GMediaHUD
        sendShowCommand(title, subtitle, iconFile.getAbsolutePath(), params);

        // Записываем все extras в лог-файл
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_LOGS, false))
            logAllExtras(sbn, notification, extras);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!isGisEnabled()) return;
//        if (!sbn.getPackageName().equals(GIS_PACKAGE)) return;
        if (!NAV_PACKAGES.contains(sbn.getPackageName())) return;

        String key = sbn.getKey();
        String tag = activeGisNotifications.remove(key);

        if (tag != null) {
            sendHideCommand(tag);
        }
    }

    private void sendShowCommand(String title, String subtitle, String iconPath, String params) {
        Intent intent = new Intent(ACTION_SHOW);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("title", title);
        intent.putExtra("subtitle", subtitle);
        intent.putExtra("art", iconPath);
        intent.putExtra("duration", 100);
        intent.putExtra("params", params);

        sendBroadcast(intent);
    }

    private void sendHideCommand(String tag) {
        Intent intent = new Intent(ACTION_HIDE);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("tag", tag);
        sendBroadcast(intent);
    }

//     Метод для записи всех extras в файл
    private void logAllExtras(StatusBarNotification sbn, Notification notification, Bundle extras) {
        if (sbn == null || notification == null || extras == null) {
//        Log.w("GisService", "logAllExtras: null argument");
            return;
        }

        StringBuilder sb = new StringBuilder();

        try {
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            sb.append("=== ").append(time).append(" ===\n");
            sb.append("Package: ").append(sbn.getPackageName()).append("\n");
            sb.append("Key: ").append(sbn.getKey()).append("\n");
            sb.append("PostTime: ").append(sbn.getPostTime()).append("\n");

            // Notification fields
            sb.append("--- Notification Fields ---\n");
            sb.append("category: ").append(notification.category).append("\n");
            sb.append("when: ").append(notification.when).append("\n");
            sb.append("flags: ").append(notification.flags).append("\n");
            sb.append("priority: ").append(notification.priority).append("\n");
            sb.append("number: ").append(notification.number).append("\n");
            sb.append("color: #").append(String.format("%06X", notification.color & 0xFFFFFF)).append("\n");

            // Bundle Extras
            sb.append("--- Bundle Extras ---\n");

            // Безопасное копирование ключей
            java.util.Set<String> keys = new java.util.HashSet<>();
            try {
                keys.addAll(extras.keySet());
            } catch (Exception e) {
                sb.append("Error reading keys: ").append(e.getMessage()).append("\n");
            }

            for (String key : keys) {
                try {
                    Object value = extras.get(key);
                    sb.append(key).append(": ");

                    if (value == null) {
                        sb.append("null");
                    } else if (value instanceof CharSequence) {
                        String text = value.toString().replace("\n", "\\n").replace("\r", "");
                        if (text.length() > 200) text = text.substring(0, 200) + "...";
                        sb.append("\"").append(text).append("\"");
                    } else if (value instanceof android.graphics.Bitmap) {
                        android.graphics.Bitmap bmp = (android.graphics.Bitmap) value;
                        sb.append("Bitmap(").append(bmp.getWidth()).append("x").append(bmp.getHeight()).append(")");
                    } else if (value instanceof android.graphics.drawable.Icon) {
                        sb.append("Icon");
                    } else if (value instanceof android.app.Notification) {
                        sb.append("Notification");
                    } else if (value instanceof java.util.ArrayList) {
                        sb.append("ArrayList[").append(((java.util.ArrayList<?>) value).size()).append("]");
                    } else if (value.getClass().isArray()) {
                        sb.append("Array[").append(java.lang.reflect.Array.getLength(value)).append("]");
                    } else {
                        String text = value.toString();
                        if (text.length() > 200) text = text.substring(0, 200) + "...";
                        sb.append(text);
                    }
                    sb.append(" (").append(value.getClass().getSimpleName()).append(")\n");

                } catch (Exception e) {
                    sb.append(key).append(": [ERROR: ").append(e.getClass().getSimpleName()).append("]\n");
                }
            }

            // Actions
            sb.append("--- Actions ---\n");
            if (notification.actions != null) {
                for (int i = 0; i < notification.actions.length; i++) {
                    try {
                        android.app.Notification.Action action = notification.actions[i];
                        sb.append("action[").append(i).append("]: ").append(action.title).append("\n");
                    } catch (Exception e) {
                        sb.append("action[").append(i).append("]: [ERROR]\n");
                    }
                }
            } else {
                sb.append("none\n");
            }

            sb.append("========================\n\n");

            // Запись в файл
            String fileName = "gis_notifications_" +
                    new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                            .format(new java.util.Date()) + ".txt";
            File logFile = new File(gisDir, fileName);

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(logFile, true); // append = true
                fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.flush();
            } catch (Exception e) {
//                Log.e("GisService", "Log write error: " + e.getMessage());
            } finally {
                if (fos != null) {
                    try { fos.close(); } catch (Exception ignored) {}
                }
            }

        } catch (Exception e) {
//        Log.e("GisService", "logAllExtras failed: " + e.getMessage(), e);
        }
    }

//     ============ Вспомогательные методы ============

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
//            Log.e(TAG, "Hash error", e);
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
        File[] files = gisDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length < MAX_ICONS) return;

        Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

        int deleteCount = files.length - (MAX_ICONS - 1);
        for (int i = 0; i < deleteCount; i++) {
            files[i].delete();
        }
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//            Log.d(TAG, "Saved icon: " + file.getName());
        } catch (Exception e) {
//            Log.e(TAG, "Save error", e);
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

    // update
    private void showUpdateDialog(UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("Доступно обновление " + info.version)
                .setMessage(info.changelog)
                .setPositiveButton("Обновить", (d, w) -> startDownload(info))
                .setNegativeButton("Позже", null)
                .show();
    }

    private void startDownload(UpdateInfo info) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Загрузка...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.show();

        UpdateDownloader.download(this, info, new UpdateDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                progress.setProgress(percent);
            }

            @Override
            public void onComplete(File apkFile) {
                progress.dismiss();
                UpdateDownloader.installApk(GisNotificationService.this, apkFile);
            }

            @Override
            public void onError(String error) {
                progress.dismiss();
                Toast.makeText(GisNotificationService.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}