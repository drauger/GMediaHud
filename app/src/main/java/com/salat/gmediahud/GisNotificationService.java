package com.salat.gmediahud;

import android.app.Notification;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GisNotificationService extends NotificationListenerService {
//    private static final String TAG = "GisNotificationService";
    private static final String PREFS_NAME = "GisServicePrefs";
    private static final String KEY_GIS_ENABLED = "gis_enabled";
    private static final String KEY_AR_ENABLED = "ar_enabled";
    private static final String KEY_SOUND = "gis_sound_enabled";
    private static final String KEY_LOGS = "gis_logs_enabled";
    private static final String ICONS_FOLDER = "GIS_Icons";

    private static final String PACKAGE_2GIS = "ru.dublgis.dgismobile";
//    private static final String PACKAGE_YANDEX_MAPS = "ru.yandex.yandexmaps";
//    private static final String PACKAGE_YANDEX_NAVIGATOR = "ru.yandex.yandexnavi";
//    private static final String PACKAGE_ANTIRADAR = "air.StrelkaSDFREE";
//    private static final String PACKAGE_HUDFREE = "air.StrelkaHUDFREE";
//    private static final String PACKAGE_HUDPREMIUM = "air.StrelkaHUDPREMIUM";

//    private static final Set<String> NAV_PACKAGES = new HashSet<>();
//    static {
//        NAV_PACKAGES.add(PACKAGE_2GIS);
//        NAV_PACKAGES.add(PACKAGE_YANDEX_MAPS);
//        NAV_PACKAGES.add(PACKAGE_YANDEX_NAVIGATOR);
//    }

//    private static final Set<String> AR_PACKAGES = new HashSet<>();
//    static {
//        AR_PACKAGES.add(PACKAGE_ANTIRADAR);
//        AR_PACKAGES.add(PACKAGE_HUDFREE);
//        AR_PACKAGES.add(PACKAGE_HUDPREMIUM);
//    }

    private static final int MAX_ICONS = 200;
    private static final String ACTION_SHOW = "com.salat.gmediahud.SHOW";
    private static final String ACTION_HIDE = "com.salat.gmediahud.HIDE";
    private static final String TARGET_PACKAGE = "com.salat.gmediahud";

    private int DISTANCE_LIMIT = 500;

    private static File gisDir;
    private final Map<String, String> activeGisNotifications = new HashMap<>();
    private int notificationCounter = 0;

    private int distance = 0;
    private String title = " ";
    private String subtitle = " ";
    private String lastTarget;
    private Bitmap iconBitmap = null;

    private NavigationReceiver navigationReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + getPackageName())));
        }

        registerNavigationReceiver();

        initDirectory();
        activeGisNotifications.clear();
        notificationCounter = 0;
    }

    private void initDirectory() {
        gisDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ICONS_FOLDER);

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (navigationReceiver != null) {
            try {
                unregisterReceiver(navigationReceiver);
            } catch (Exception e) {
//                Log.e(TAG, "Error unregistering receiver", e);
            }
            navigationReceiver = null;
        }
        NavigationReceiver.setListener(null);
    }

    private void registerNavigationReceiver() {
        navigationReceiver = new NavigationReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(NavigationReceiver.ACTION_YANDEX_MANEUVER);
        filter.addAction(NavigationReceiver.ACTION_YANDEX_NEXT_TEXT);
        filter.addAction(NavigationReceiver.ACTION_YANDEX_NEXT_STREET);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_SPEEDLIMIT);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_ARRIVAL);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_DISTANCE);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_TIME);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_NAV_ACTIVE);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_ROADCAMERA);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_TRAFFICLIGHT);
//        filter.addAction(NavigationReceiver.ACTION_YANDEX_ROUTE_POLYLINE);
//        filter.addAction(NavigationReceiver.ACTION_ANTIRADAR_UPDATE);
        filter.addAction(NavigationReceiver.ACTION_HUDSPEED_UPDATE);

        NavigationReceiver.setListener(new NavigationReceiver.NavigationListener() {
            @Override
            public void onYandexManeuver(String type, Bitmap bitmap) {
                if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GIS_ENABLED, false)) return;

                // Тип манёвра + иконка
                if (bitmap != null) {
                    iconBitmap = bitmap;
                }
            }

            @Override
            public void onYandexNextText(String text) {
                if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GIS_ENABLED, false)) return;

                // Расстояние до манёвра, например "300 м"
                subtitle = text;
                distance = Integer.parseInt((subtitle.split(" ", 2))[0].trim());

                if (distance <= DISTANCE_LIMIT && !subtitle.contains("км"))
                    createNotification("ya");
            }

            @Override
            public void onYandexNextStreet(String street) {
                if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GIS_ENABLED, false)) return;

                // Название улицы/дороги
                title = street;
            }

//            @Override
//            public void onYandexSpeedLimit(String limit) {
//                // Ограничение скорости
//            }
//
//            @Override
//            public void onYandexArrival(String arrival) {
//                // Время прибытия
//            }
//
//            @Override
//            public void onYandexDistance(String distance) {
//                // Оставшееся расстояние до пункта назначения
//            }
//
//            @Override
//            public void onYandexTime(String time) {
//                // Оставшееся время в пути
//            }
//
//            @Override
//            public void onYandexNavActive(boolean isActive) {
//                // Состояние навигации (диагностика)
//            }
//
//            @Override
//            public void onYandexRoadCamera(String cameraId, String distance, Bitmap icon) {
//                // Дорожная камера
//            }
//
//            @Override
//            public void onYandexTrafficLight(int id, boolean visible, String color, String countdown, Bitmap arrowBitmap, String arrowDirection, long timestamp) {
//                // Светофор: color = "red"/"green"/"yellow", countdown = "15"
//            }
//
//            @Override
//            public void onYandexRoutePolyline(boolean active, String routeId, double[] lats, double[] lons, int count) {
//                // Полилиния маршрута
//            }

            @Override
            public void onHudSpeedUpdate(boolean hasCamera, boolean hasGps, int distance, int limit1, int limit2, int camType, int camFlag) {
                if (hasCamera) {
                    if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_AR_ENABLED, false)) return;

                    String title = " ";

                    switch (camFlag) {
                        case 1:
                            title = "Камера в лицо";
                            break;
                        case 3:
                            title = "Камера в спину";
                            break;
                        case 4:
                            title = "Камера <>";
                            break;
                        default:
//                            title = " ";
                    }

                    if (limit1 > 0)
                        title += " на " + limit1;

                    String tag = "antiradar";
                    String iconPath = "";

                    String iconFileName = "cam_type_" + camType + ".png";
                    File iconFile = new File(gisDir, iconFileName);

                    if (!iconFile.exists()) {
                        copyIcon(getApplicationContext(), iconFileName, iconFileName);
                    }

                    String params = "queue=0,warning=";
                    params += 0;    //getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SOUND, false) ? 1 : 0;
                    params += ",source=6,toast=0,tag=";
                    params += tag;

                    // Отправляем broadcast в GMediaHUD
                    sendShowCommand(title, distance + " м", iconFile.getAbsolutePath(), 5, params);
                }
                else {
                    sendHideCommand("antiradar");
                }
            }
        });

        registerReceiver(navigationReceiver, filter);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        if (!PACKAGE_2GIS.equals(packageName) || !getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GIS_ENABLED, false)) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString();
        String[] parts = (text.split("\n", 2))[0].trim().split("\\s*?[-\\u2013\\u2014]\\s*?", 2);
        subtitle = parts[0].trim();
        title = parts.length > 1 ? parts[1].trim() : " ";
        distance = Integer.parseInt((subtitle.split(" ", 2))[0].trim());

        if (distance > DISTANCE_LIMIT || subtitle.contains("км")) return;

        Icon largeIcon = notification.getLargeIcon();
        if (largeIcon != null) {
            Drawable drawable = largeIcon.loadDrawable(this);
            if (drawable != null) {
                iconBitmap = drawableToBitmap(drawable);
            }
        }

        createNotification("2gis");

        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_LOGS, false))
            logAllExtras(sbn, notification, extras);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!PACKAGE_2GIS.equals(sbn.getPackageName())) return;

        String key = sbn.getKey();
        String tag = activeGisNotifications.remove(key);

        if (tag != null) {
            sendHideCommand(tag);
        }
    }

    private void createNotification(String key) {
        int warning = 0;

        if (!title.equals(lastTarget)) {
            warning = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SOUND, false) ? 1 : 0;
            lastTarget = title;
        }

        // Получаем имя файла и сохраняем
        String iconFileName = getIconFileName(iconBitmap);
        File iconFile = new File(gisDir, iconFileName);

        if (!iconFile.exists() && iconBitmap != null && !iconBitmap.isRecycled()) {
            enforceMaxIconsLimit();
            if (key.equals("ya"))
                iconBitmap = addBackgroundToBitmap(iconBitmap, Color.rgb(0, 132, 80));
            saveBitmap(iconBitmap, iconFile);
        }

        String tag = key + (++notificationCounter);
        activeGisNotifications.put(key, tag);

        String params = "queue=0,warning=";
        params += warning;
        params += ",source=6,toast=0,tag=";
        params += tag;

        // Отправляем broadcast в GMediaHUD
        sendShowCommand(title, subtitle, iconFile.getAbsolutePath(), 10, params);
    }

    private void sendShowCommand(String title, String subtitle, String iconPath, int duration, String params) {
        Intent intent = new Intent(ACTION_SHOW);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("title", title);
        intent.putExtra("subtitle", subtitle);
        intent.putExtra("art", iconPath);
        intent.putExtra("duration", duration);
        intent.putExtra("params", params);

        sendBroadcast(intent);
    }

    private void sendHideCommand(String tag) {
        Intent intent = new Intent(ACTION_HIDE);
        intent.setPackage(TARGET_PACKAGE);
        intent.putExtra("tag", tag);
        sendBroadcast(intent);
    }

//     ============ Вспомогательные методы ============

    private String getIconFileName(Bitmap bitmap) {
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

    public Bitmap addBackgroundToBitmap(Bitmap originalBitmap, int backgroundColor) {
        if (originalBitmap == null) return null;

        // Создаём новый Bitmap с тем же размером
        Bitmap result = Bitmap.createBitmap(
                originalBitmap.getWidth(),
                originalBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(result);

        // Рисуем фон
        canvas.drawColor(backgroundColor);

        // Рисуем оригинальный Bitmap поверх
        canvas.drawBitmap(originalBitmap, 0, 0, null);

        return result;
    }

    private static boolean copyIcon(Context context, String assetFilePath, String fileName) {
        try {
            File destFile = new File(gisDir, fileName);
            try (InputStream in = context.getAssets().open(assetFilePath);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                copyStream(in, out);
            }
            return true;

        } catch (IOException e) {
//            Log.e(TAG, "Legacy copy failed for " + fileName + ": " + e.getMessage(), e);
            return false;
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
    }

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

}