package com.salat.gmediahud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
//import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NavigationReceiver extends BroadcastReceiver {
    private static final String TAG = "NavigationReceiver";

    public static final String ACTION_YANDEX_MANEUVER       = "com.yandex.MANEUVER";
    public static final String ACTION_YANDEX_NEXT_TEXT      = "com.yandex.NIXT";
    public static final String ACTION_YANDEX_NEXT_STREET    = "com.yandex.NEXTSTREET";
//    public static final String ACTION_YANDEX_SPEEDLIMIT     = "com.yandex.SPEEDLIMIT";
//    public static final String ACTION_YANDEX_ARRIVAL        = "com.yandex.ARRIVAL";
//    public static final String ACTION_YANDEX_DISTANCE       = "com.yandex.DISTANCE";
//    public static final String ACTION_YANDEX_TIME           = "com.yandex.TIME";
//    public static final String ACTION_YANDEX_NAV_ACTIVE     = "com.yandex.NAV_ACTIVE";
//    public static final String ACTION_YANDEX_ROADCAMERA     = "com.yandex.ROADCAMERA";
//    public static final String ACTION_YANDEX_TRAFFICLIGHT   = "com.yandex.TRAFFICLIGHT";
//    public static final String ACTION_YANDEX_ROUTE_POLYLINE = "com.yandex.ROUTE_POLYLINE";

    public static final String ACTION_HUDSPEED_UPDATE       = "air.strelkasd.CAMERA_INFO_CHANGED";

    // --- Extras: Яндекс ---
    public static final String EXTRA_MANEUVER_BITMAP       = "maneuver_bitmap";
    public static final String EXTRA_MANEUVER_TYPE         = "maneuver_type";
    public static final String EXTRA_NEXT_TEXT             = "next_text";
    public static final String EXTRA_NEXT_STREET           = "next_street";
//    public static final String EXTRA_SPEEDLIMIT_TEXT       = "speedlimit_text";
//    public static final String EXTRA_ARRIVAL_TEXT          = "Arrival_text";
//    public static final String EXTRA_DISTANCE_TEXT         = "Distance_text";
//    public static final String EXTRA_TIME_TEXT             = "Time_text";
//    public static final String EXTRA_NAV_IS_ACTIVE         = "is_active";
//    public static final String EXTRA_CAMERA_ID             = "camera_id";
//    public static final String EXTRA_CAMERA_DISTANCE       = "distance_text";
//    public static final String EXTRA_CAMERA_ICON           = "camera_icon";
//    public static final String EXTRA_TRAFFIC_SIGNAL_COLOR  = "signal_color";
//    public static final String EXTRA_TRAFFIC_COUNTDOWN     = "countdown";
//    public static final String EXTRA_TRAFFIC_TIMESTAMP     = "timestamp";
//    public static final String EXTRA_TRAFFIC_ARROW_BITMAP  = "arrow_bitmap";
//    public static final String EXTRA_TRAFFIC_ARROW_DIRECTION = "arrow_direction";
//    public static final String EXTRA_TRAFFIC_LIGHT_ID      = "traffic_light_id";
//    public static final String EXTRA_TRAFFIC_IS_VISIBLE    = "is_visible";
//    public static final String EXTRA_ROUTE_ACTIVE_FLAG     = "route_active";
//    public static final String EXTRA_ROUTE_ID              = "route_id";
//    public static final String EXTRA_POLYLINE_LATS         = "polyline_lats";
//    public static final String EXTRA_POLYLINE_LONS         = "polyline_lons";
//    public static final String EXTRA_POLYLINE_COUNT        = "polyline_count";

    // --- Extras: HUD Speed (air.strelkasd) ---
    public static final String HUDSPEED_HAS_CAMERA = "hasCamera";
    public static final String HUDSPEED_HAS_GPS    = "hasGps";
    public static final String HUDSPEED_DISTANCE   = "distance";
    public static final String HUDSPEED_LIMIT_1    = "limit1";
    public static final String HUDSPEED_LIMIT_2    = "limit2";
    public static final String HUDSPEED_CAM_TYPE   = "camType";
    public static final String HUDSPEED_CAM_FLAG   = "camFlag";

    // --- Callback-интерфейс ---
    public interface NavigationListener {
        void onYandexManeuver(String type, Bitmap bitmap);
        void onYandexNextText(String text);
        void onYandexNextStreet(String street);
//        void onYandexSpeedLimit(String limit);
//        void onYandexArrival(String arrival);
//        void onYandexDistance(String distance);
//        void onYandexTime(String time);
//        void onYandexNavActive(boolean isActive);
//        void onYandexRoadCamera(String cameraId, String distance, Bitmap icon);
//        void onYandexTrafficLight(int id, boolean visible, String color, String countdown,
//                                  Bitmap arrowBitmap, String arrowDirection, long timestamp);
//        void onYandexRoutePolyline(boolean active, String routeId,
//                                   double[] lats, double[] lons, int count);
        void onHudSpeedUpdate(boolean hasCamera, boolean hasGps, int distance,
                              int limit1, int limit2, int camType, int camFlag);
    }

    @Nullable
    private static NavigationListener listener;

    public static void setListener(@Nullable NavigationListener l) {
        listener = l;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

//        Log.d(TAG, "Received: " + action + " extras=" + formatExtras(intent));

        switch (action) {
            case ACTION_YANDEX_MANEUVER:
                handleYandexManeuver(intent);
                break;

            case ACTION_YANDEX_NEXT_TEXT:
                handleYandexNextText(intent);
                break;

            case ACTION_YANDEX_NEXT_STREET:
                handleYandexNextStreet(intent);
                break;

//            case ACTION_YANDEX_SPEEDLIMIT:
//                handleYandexSpeedLimit(intent);
//                break;
//
//            case ACTION_YANDEX_ARRIVAL:
//                handleYandexArrival(intent);
//                break;
//
//            case ACTION_YANDEX_DISTANCE:
//                handleYandexDistance(intent);
//                break;
//
//            case ACTION_YANDEX_TIME:
//                handleYandexTime(intent);
//                break;
//
//            case ACTION_YANDEX_NAV_ACTIVE:
//                handleYandexNavActive(intent);
//                break;
//
//            case ACTION_YANDEX_ROADCAMERA:
//                handleYandexRoadCamera(intent);
//                break;
//
//            case ACTION_YANDEX_TRAFFICLIGHT:
//                handleYandexTrafficLight(intent);
//                break;
//
//            case ACTION_YANDEX_ROUTE_POLYLINE:
//                handleYandexRoutePolyline(intent);
//                break;

            case ACTION_HUDSPEED_UPDATE:
                handleHudSpeed(intent);
                break;
        }
    }

    // ========================= Обработчики =========================

    private void handleYandexManeuver(Intent intent) {
        Bitmap bmp = getBitmapExtra(intent, EXTRA_MANEUVER_BITMAP);
        String type = normalize(intent.getStringExtra(EXTRA_MANEUVER_TYPE));
//        Log.d(TAG, "Yandex maneuver: type=" + type + " bitmap=" + (bmp != null ? bmp.getWidth() + "x" + bmp.getHeight() : "null"));
        if (listener != null) listener.onYandexManeuver(type, bmp);
    }

    private void handleYandexNextText(Intent intent) {
        String text = normalize(intent.getStringExtra(EXTRA_NEXT_TEXT));
//        Log.d(TAG, "Yandex next_text: " + text);
        if (listener != null) listener.onYandexNextText(text);
    }

    private void handleYandexNextStreet(Intent intent) {
        String street = normalize(intent.getStringExtra(EXTRA_NEXT_STREET));
//        Log.d(TAG, "Yandex next_street: " + street);
        if (listener != null) listener.onYandexNextStreet(street);
    }

//    private void handleYandexSpeedLimit(Intent intent) {
//        String limit = normalize(intent.getStringExtra(EXTRA_SPEEDLIMIT_TEXT));
//        Log.d(TAG, "Yandex speedlimit: " + limit);
//        if (listener != null) listener.onYandexSpeedLimit(limit);
//    }
//
//    private void handleYandexArrival(Intent intent) {
//        String arrival = normalize(intent.getStringExtra(EXTRA_ARRIVAL_TEXT));
//        Log.d(TAG, "Yandex arrival: " + arrival);
//        if (listener != null) listener.onYandexArrival(arrival);
//    }
//
//    private void handleYandexDistance(Intent intent) {
//        String distance = normalize(intent.getStringExtra(EXTRA_DISTANCE_TEXT));
//        Log.d(TAG, "Yandex distance: " + distance);
//        if (listener != null) listener.onYandexDistance(distance);
//    }
//
//    private void handleYandexTime(Intent intent) {
//        String time = normalize(intent.getStringExtra(EXTRA_TIME_TEXT));
//        Log.d(TAG, "Yandex time: " + time);
//        if (listener != null) listener.onYandexTime(time);
//    }
//
//    private void handleYandexNavActive(Intent intent) {
//        boolean active = intent.getBooleanExtra(EXTRA_NAV_IS_ACTIVE, false);
//        Log.d(TAG, "Yandex nav active: " + active);
//        if (listener != null) listener.onYandexNavActive(active);
//    }
//
//    private void handleYandexRoadCamera(Intent intent) {
//        String cameraId = normalize(intent.getStringExtra(EXTRA_CAMERA_ID));
//        String distance = normalize(intent.getStringExtra(EXTRA_CAMERA_DISTANCE));
//        Bitmap icon = getBitmapExtra(intent, EXTRA_CAMERA_ICON);
//        Log.d(TAG, "Yandex road camera: id=" + cameraId + " distance=" + distance);
//        if (listener != null) listener.onYandexRoadCamera(cameraId, distance, icon);
//    }
//
//    private void handleYandexTrafficLight(Intent intent) {
//        int id = intent.getIntExtra(EXTRA_TRAFFIC_LIGHT_ID, 0);
//        boolean visible = intent.getBooleanExtra(EXTRA_TRAFFIC_IS_VISIBLE, true);
//        String color = normalize(intent.getStringExtra(EXTRA_TRAFFIC_SIGNAL_COLOR));
//        String countdown = normalize(intent.getStringExtra(EXTRA_TRAFFIC_COUNTDOWN));
//        long timestamp = intent.getLongExtra(EXTRA_TRAFFIC_TIMESTAMP, 0L);
//        Bitmap arrow = getBitmapExtra(intent, EXTRA_TRAFFIC_ARROW_BITMAP);
//        String arrowDir = normalize(intent.getStringExtra(EXTRA_TRAFFIC_ARROW_DIRECTION));
//        Log.d(TAG, "Yandex traffic light: color=" + color + " countdown=" + countdown);
//        if (listener != null) listener.onYandexTrafficLight(id, visible, color, countdown, arrow, arrowDir, timestamp);
//    }
//
//    private void handleYandexRoutePolyline(Intent intent) {
//        boolean active = intent.getBooleanExtra(EXTRA_ROUTE_ACTIVE_FLAG, false);
//        String routeId = normalize(intent.getStringExtra(EXTRA_ROUTE_ID));
//        double[] lats = intent.getDoubleArrayExtra(EXTRA_POLYLINE_LATS);
//        double[] lons = intent.getDoubleArrayExtra(EXTRA_POLYLINE_LONS);
//        int count = intent.getIntExtra(EXTRA_POLYLINE_COUNT, 0);
//        Log.d(TAG, "Yandex polyline: active=" + active + " points=" + count);
//        if (listener != null) listener.onYandexRoutePolyline(active, routeId, lats, lons, count);
//    }

    private void handleHudSpeed(Intent intent) {
        boolean hasCamera = intent.getBooleanExtra(HUDSPEED_HAS_CAMERA, false);
        boolean hasGps = intent.getBooleanExtra(HUDSPEED_HAS_GPS, false);
        int distance = intent.getIntExtra(HUDSPEED_DISTANCE, -1);
        int limit1 = intent.getIntExtra(HUDSPEED_LIMIT_1, -1);
        int limit2 = intent.getIntExtra(HUDSPEED_LIMIT_2, -1);
        int camType = intent.getIntExtra(HUDSPEED_CAM_TYPE, -1);
        int camFlag = intent.getIntExtra(HUDSPEED_CAM_FLAG, -1);
//        Log.d(TAG, "HUDSpeed: hasCamera=" + hasCamera + " limit1=" + limit1);
        if (listener != null) listener.onHudSpeedUpdate(hasCamera, hasGps, distance, limit1, limit2, camType, camFlag);
    }

    // ========================= Утилиты =========================

    @NonNull
    private static String normalize(@Nullable String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Nullable
    private static Bitmap getBitmapExtra(@NonNull Intent intent, @NonNull String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Bitmap.class);
        } else {
            //noinspection deprecation
            return intent.getParcelableExtra(key);
        }
    }

//    @NonNull
//    private static String formatExtras(@NonNull Intent intent) {
//        Bundle extras = intent.getExtras();
//        if (extras == null) return "{}";
//        StringBuilder sb = new StringBuilder("{");
//        boolean first = true;
//        for (String key : extras.keySet()) {
//            if (!first) sb.append(", ");
//            first = false;
//            Object value = extras.get(key);
//            String desc;
//            if (value instanceof Bitmap) {
//                Bitmap b = (Bitmap) value;
//                desc = "Bitmap(" + b.getWidth() + "x" + b.getHeight() + ")";
//            } else if (value instanceof Bundle) {
//                desc = "Bundle(keys=" + ((Bundle) value).keySet().size() + ")";
//            } else if (value != null && value.getClass().isArray()) {
//                desc = value.getClass().getSimpleName() + "(len=" + java.lang.reflect.Array.getLength(value) + ")";
//            } else {
//                desc = String.valueOf(value);
//            }
//            sb.append(key).append("=").append(desc);
//        }
//        sb.append("}");
//        return sb.toString();
//    }
}
