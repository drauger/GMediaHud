package com.salat.gmediahud

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salat.gmediahud.components.isNotificationServiceEnabled
import com.salat.gmediahud.components.isPackageInstalled
import com.salat.gmediahud.components.openAccessibilitySettings
import com.salat.gmediahud.components.openAppSystemSettings
import com.salat.gmediahud.components.requestNotificationServicePermission
import com.salat.gmediahud.datastore.DataStoreManager
import com.salat.gmediahud.datastore.Prefs
import com.salat.gmediahud.entity.CustomSourceType
import com.salat.gmediahud.ui.BaseButton
import com.salat.gmediahud.ui.RenderSwitcher
import com.salat.gmediahud.ui.ValueSlider
import com.salat.gmediahud.ui.theme.AppTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val DEFAULT_UPDATE_RATE = 1000
private const val DEFAULT_MEDIA_SOURCE = 6
private const val DEFAULT_NOTIFICATION_VOLUME = 90
private const val DEFAULT_GIS_NOTIFICATION_DISTANCE = 500
private const val DEFAULT_GIS_NOTIFICATION_TIMEOUT = 10
private const val DEFAULT_AR_NOTIFICATION_TIMEOUT = 5
private const val SUCHII_ES_PROVODNIK = "com.estrongs.android.pop"

class MainActivity : ComponentActivity() {

    private var updateInfo by mutableStateOf<UpdateInfo?>(null)
    private var downloadProgress by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.Transparent.toArgb()),
            SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        val ds = DataStoreManager(this)

        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + getPackageName()))
            )
        }

        setContent {
            val uiScale = 1.5f
            val context = LocalContext.current
            val density = LocalDensity.current
            val scaledDensity = remember(density, uiScale) {
                Density(
                    density.density * uiScale,
                    density.fontScale * uiScale
                )
            }

            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = { Text("Доступно обновление ${info.version}") },
                    text = { Text(info.changelog) },
                    confirmButton = {
                        BaseButton(
                            title = "Обновить",
                            onClick = {
                                updateInfo = null
                                startDownload(info)
                            }
                        )
                    },
                    dismissButton = {
                        BaseButton(
                            title = "Позже",
                            onClick = { updateInfo = null }
                        )
                    }
                )
            }

            if (downloadProgress >= 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.surfaceBackground)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Загрузка... $downloadProgress%",
                            style = AppTheme.typography.screenTitle,
                            color = AppTheme.colors.contentPrimary
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppTheme.colors.contentAccent,
                            trackColor = AppTheme.colors.sliderPassive
                        )
                    }
                }
            }

            AppTheme(
                darkTheme = true
            ) {
                val scope = rememberCoroutineScope()
                val canAccessibility by GlobalState.canAccessibility.collectAsStateWithLifecycle()

                val editor = getSharedPreferences("GisServicePrefs", Context.MODE_PRIVATE).edit()

                var isNotificationServiceEnabled by remember {
                    mutableStateOf(context.isNotificationServiceEnabled())
                }
                // var needFilePermission by remember { mutableStateOf(false) }

                var updateRate by remember { mutableIntStateOf(DEFAULT_UPDATE_RATE) }
                var mediaSource by remember { mutableIntStateOf(DEFAULT_MEDIA_SOURCE) }
                var notificationVolume by remember { mutableIntStateOf(DEFAULT_NOTIFICATION_VOLUME) }
                var isEnabled by remember { mutableStateOf(false) }
                var forceUpdate by remember { mutableStateOf(false) }
                var unknownArtistStub by remember { mutableStateOf(false) }
                var esExplorerStub by remember { mutableStateOf(false) }
                var filterByAudioSource by remember { mutableStateOf(false) }
                var gisNotificationsEnabled by remember { mutableStateOf(false) }
                var arNotificationsEnabled by remember { mutableStateOf(false) }
                var gisSoundEnabled by remember { mutableStateOf(false) }
                var gisLogsEnabled by remember { mutableStateOf(false) }
                var gisNotificationsDistance by remember { mutableIntStateOf(DEFAULT_GIS_NOTIFICATION_DISTANCE) }
                var gisNotificationsTimeout by remember { mutableIntStateOf(DEFAULT_GIS_NOTIFICATION_TIMEOUT) }
                var arNotificationsTimeout by remember { mutableIntStateOf(DEFAULT_AR_NOTIFICATION_TIMEOUT) }

                LaunchedEffect(true) {
                    checkPermissions()
                    checkNotificationAccess()

                    updateRate =
                        ds.getValueFlow(Prefs.UPDATE_RATE).firstOrNull() ?: DEFAULT_UPDATE_RATE
                    mediaSource =
                        ds.getValueFlow(Prefs.MEDIA_SOURCE).firstOrNull() ?: DEFAULT_MEDIA_SOURCE
                    notificationVolume =
                        ds.getValueFlow(Prefs.NOTIFICATION_VOLUME).firstOrNull() ?: DEFAULT_NOTIFICATION_VOLUME
                    isEnabled =
                        ds.getValueFlow(Prefs.MEDIA_DATA_SYNC_ENABLED).firstOrNull() ?: false
                    forceUpdate =
                        ds.getValueFlow(Prefs.FORCE_SYNC).firstOrNull() ?: false
                    unknownArtistStub =
                        ds.getValueFlow(Prefs.UNKNOWN_ARTIST_STUB).firstOrNull() ?: false
                    filterByAudioSource =
                        ds.getValueFlow(Prefs.FILTER_BY_AUDIO_SOURCE).firstOrNull() ?: false

                    gisNotificationsEnabled =
                        ds.getValueFlow(Prefs.GIS_NOTIFICATIONS_ENABLED).firstOrNull() ?: false
                    editor.putBoolean("gis_enabled", gisNotificationsEnabled)

                    arNotificationsEnabled =
                        ds.getValueFlow(Prefs.AR_NOTIFICATIONS_ENABLED).firstOrNull() ?: false
                    editor.putBoolean("ar_enabled", arNotificationsEnabled)

                    gisSoundEnabled =
                        ds.getValueFlow(Prefs.GIS_SOUND_ENABLED).firstOrNull() ?: false
                    editor.putBoolean("gis_sound_enabled", gisSoundEnabled)

                    gisLogsEnabled =
                        ds.getValueFlow(Prefs.GIS_LOGS_ENABLED).firstOrNull() ?: false
                    editor.putBoolean("gis_logs_enabled", gisLogsEnabled)

                    gisNotificationsDistance =
                        ds.getValueFlow(Prefs.GIS_NOTIFICATIONS_DISTANCE).firstOrNull() ?: DEFAULT_GIS_NOTIFICATION_DISTANCE
                    editor.putInt("gis_notifications_distance", gisNotificationsDistance)

                    gisNotificationsTimeout =
                        ds.getValueFlow(Prefs.GIS_NOTIFICATIONS_TIMEOUT).firstOrNull() ?: DEFAULT_GIS_NOTIFICATION_TIMEOUT
                    editor.putInt("gis_notifications_timeout", gisNotificationsTimeout)

                    arNotificationsTimeout =
                        ds.getValueFlow(Prefs.AR_NOTIFICATIONS_TIMEOUT).firstOrNull() ?: DEFAULT_AR_NOTIFICATION_TIMEOUT
                    editor.putInt("ar_notifications_timeout", arNotificationsTimeout)

                    editor.apply()
                }
                LaunchedEffect(true) {
                    if (!esExplorerStub) {
                        esExplorerStub = context.isPackageInstalled(SUCHII_ES_PROVODNIK)
                    }
                }

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                        val sourceTypeSelectDialog = remember { mutableStateOf(false) }
                        if (sourceTypeSelectDialog.value) {
                            SourceSelectDialog(
                                selected = Pair(mediaSource, mediaSource.getSourceName()),
                                list = sources.associateWith { it.getSourceName() },
                                uiScaleState = uiScale,
                                onDismiss = { sourceTypeSelectDialog.value = false },
                                onCancel = { sourceTypeSelectDialog.value = false },
                                onSelect = { pair ->
                                    pair?.let {
                                        mediaSource = it.first
                                        scope.launch {
                                            ds.saveValue(Prefs.MEDIA_SOURCE, it.first)
                                        }
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppTheme.colors.surfaceBackground)
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (esExplorerStub) {

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.es_explorer_warning),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        BaseButton(
                                            title = stringResource(R.string.uninstall),
                                            onClick = {
                                                context.openAppSystemSettings(SUCHII_ES_PROVODNIK)
                                            })
                                        Spacer(Modifier.width(26.dp))
                                        BaseButton(
                                            title = stringResource(R.string.ok),
                                            onClick = { esExplorerStub = false })
                                    }
                                }

                            } else if (!isNotificationServiceEnabled) {

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.grant_notification_access),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    BaseButton(
                                        title = stringResource(R.string.settings),
                                        onClick = { context.requestNotificationServicePermission() })
                                }
                            } else if (!canAccessibility) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.enable_accessibility),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    BaseButton(
                                        title = stringResource(R.string.accessibility_features),
                                        onClick = { context.openAccessibilitySettings() })
                                }
                            } else {
                                Spacer(Modifier.height(26.dp))

                                Text(
                                    modifier = Modifier,
                                    text = stringResource(
                                        if (isEnabled) {
                                            R.string.mediahud_active
                                        } else {
                                            R.string.mediahud_disabled
                                        }
                                    ),
                                    style = AppTheme.typography.dialogTitle,
                                    color = if (isEnabled) {
                                        AppTheme.colors.contentAccent
                                    } else {
                                        AppTheme.colors.sliderPassive
                                    }
                                )

                                Spacer(Modifier.height(48.dp))

                                // is enable
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.enable),
                                    subtitle = stringResource(R.string.media_data_sync),
                                    value = isEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        isEnabled = it
                                        scope.launch {
                                            ds.saveValue(Prefs.MEDIA_DATA_SYNC_ENABLED, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Sync rate
                                val sliderTitle = stringResource(R.string.refresh_rate)
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$sliderTitle: " +
                                            updateRate.toDecimalSecondString(),
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = updateRate,
                                    valueRange = 600..5000,
                                    onValueChange = { newValue ->
                                        updateRate = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.UPDATE_RATE, newValue)
                                        }
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_UPDATE_RATE,
                                    step = 100
                                )

                                Spacer(Modifier.height(12.dp))

                                // Force update
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.force_update),
                                    subtitle = stringResource(R.string.update_all_media_data),
                                    value = forceUpdate,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        forceUpdate = it
                                        scope.launch {
                                            ds.saveValue(Prefs.FORCE_SYNC, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(12.dp))

                                // Media source
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            sourceTypeSelectDialog.value = true
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            modifier = Modifier.padding(horizontal = 23.dp),
                                            text = stringResource(R.string.media_source),
                                            style = AppTheme.typography.screenTitle,
                                            color = AppTheme.colors.contentPrimary
                                        )

                                        Spacer(Modifier.height(5.dp))

                                        Text(
                                            text = stringResource(R.string.car_media_source_assumption),
                                            modifier = Modifier.padding(horizontal = 23.dp),
                                            color = AppTheme.colors.contentPrimary.copy(.4f),
                                            style = AppTheme.typography.dialogSubtitle
                                        )
                                    }

                                    Text(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppTheme.colors.autoStart)
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        text = mediaSource.getSourceName(),
                                        color = AppTheme.colors.contentPrimary,
                                        style = AppTheme.typography.sourceType
                                    )

                                    Spacer(Modifier.width(20.dp))
                                }

                                Spacer(Modifier.height(12.dp))

                                // Unknown ArtistStub
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.subtitle_placeholder),
                                    subtitle = stringResource(R.string.remove_unknown_artist),
                                    value = unknownArtistStub,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        unknownArtistStub = it
                                        scope.launch {
                                            ds.saveValue(Prefs.UNKNOWN_ARTIST_STUB, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Sync rate
                                val notificationVolumeTitle =
                                    stringResource(R.string.notification_volume)
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$notificationVolumeTitle: $notificationVolume%",
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = notificationVolume,
                                    valueRange = 0..100,
                                    onValueChange = { newValue ->
                                        notificationVolume = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.NOTIFICATION_VOLUME, newValue)
                                        }
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_NOTIFICATION_VOLUME,
                                    step = 1
                                )

                                Spacer(Modifier.height(12.dp))

                                // Audio source filters
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.audio_source_filter),
                                    subtitle = stringResource(R.string.audio_source_filter_desc),
                                    value = filterByAudioSource,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        filterByAudioSource = it
                                        scope.launch {
                                            ds.saveValue(Prefs.FILTER_BY_AUDIO_SOURCE, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(12.dp))

                                // Navigation
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = "Навигация",
                                    subtitle = "Показывать манёвры на панели приборов",
                                    value = gisNotificationsEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = { enabled ->
                                        gisNotificationsEnabled = enabled
                                        scope.launch {
                                            ds.saveValue(Prefs.GIS_NOTIFICATIONS_ENABLED, enabled)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putBoolean("gis_enabled", enabled).apply()

                                        if (enabled) {
                                            val cn = ComponentName(this@MainActivity, GisNotificationService::class.java)
                                            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                                            if (flat == null || !flat.contains(cn.flattenToString())) {
                                                Toast.makeText(this@MainActivity,
                                                    "Включите доступ к уведомлениям",
                                                    Toast.LENGTH_LONG).show()
                                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                            }
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Navigation notification distance
                                val gisNotificationDistanceTitle = "Дистанция предупреждения о манёвре"
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$gisNotificationDistanceTitle: $gisNotificationsDistance м",
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = gisNotificationsDistance,
                                    valueRange = 100..5000,
                                    onValueChange = { newValue ->
                                        gisNotificationsDistance = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.GIS_NOTIFICATIONS_DISTANCE, newValue)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putInt("gis_notifications_distance", newValue).apply()
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_GIS_NOTIFICATION_DISTANCE,
                                    step = 50
                                )

                                Spacer(Modifier.height(24.dp))

                                // Navigation notification timeout
                                val gisNotificationTimeoutTitle = "Задержка скрытия предупреждения о манёвре"
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$gisNotificationTimeoutTitle: $gisNotificationsTimeout с",
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = gisNotificationsTimeout,
                                    valueRange = 0..50,
                                    onValueChange = { newValue ->
                                        gisNotificationsTimeout = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.GIS_NOTIFICATIONS_TIMEOUT, newValue)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putInt("gis_notifications_timeout", newValue).apply()
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_GIS_NOTIFICATION_TIMEOUT,
                                    step = 1
                                )

                                Spacer(Modifier.height(12.dp))

                                // Navigation sound
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = "Звук навигации",
                                    subtitle = "Звуковое предупреждение о манёвре",
                                    value = gisSoundEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = { enabled ->
                                        gisSoundEnabled = enabled
                                        scope.launch {
                                            ds.saveValue(Prefs.GIS_SOUND_ENABLED, enabled)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putBoolean("gis_sound_enabled", enabled).apply()
                                    }
                                )

                                Spacer(Modifier.height(12.dp))

                                // Cameras
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = "Камеры",
                                    subtitle = "Показывать предупреждения на панели приборов",
                                    value = arNotificationsEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = { enabled ->
                                        arNotificationsEnabled = enabled
                                        scope.launch {
                                            ds.saveValue(Prefs.AR_NOTIFICATIONS_ENABLED, enabled)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putBoolean("ar_enabled", enabled).apply()

                                        if (enabled) {
                                            val cn = ComponentName(this@MainActivity, GisNotificationService::class.java)
                                            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                                            if (flat == null || !flat.contains(cn.flattenToString())) {
                                                Toast.makeText(this@MainActivity,
                                                    "Включите доступ к уведомлениям",
                                                    Toast.LENGTH_LONG).show()
                                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                            }
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Camera notification timeout
                                val arNotificationTimeoutTitle = "Задержка скрытия предупреждения о камере"
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$arNotificationTimeoutTitle: $arNotificationsTimeout с",
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = arNotificationsTimeout,
                                    valueRange = 0..50,
                                    onValueChange = { newValue ->
                                        arNotificationsTimeout = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.AR_NOTIFICATIONS_TIMEOUT, newValue)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        editor.putInt("ar_notifications_timeout", newValue).apply()
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_AR_NOTIFICATION_TIMEOUT,
                                    step = 1
                                )

                                /*Spacer(Modifier.height(12.dp))

                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = "Лог подсказок навигации",
                                    subtitle = "Сохранять все подсказки в файл",
                                    value = gisLogsEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = { enabled ->
                                        gisLogsEnabled = enabled
                                        scope.launch {
                                            ds.saveValue(Prefs.GIS_LOGS_ENABLED, enabled)
                                        }
                                        // Сохраняем в SharedPreferences для Java-сервиса
                                        getSharedPreferences("GisServicePrefs", Context.MODE_PRIVATE)
                                            .edit().putBoolean("gis_logs_enabled", enabled).apply()
                                    }
                                )*/

                                /*Spacer(Modifier.height(24.dp))

                                BaseButton(
                                    title = "Проверить обновления",
                                    onClick = {
                                        Toast.makeText(this@MainActivity, "Проверка...", Toast.LENGTH_SHORT).show()
                                        checkUpdates()
                                    }
                                )*/

                                /*if (needFilePermission) {
                                    Spacer(Modifier.height(20.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 36.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                shape = RoundedCornerShape(16.dp),
                                                width = 1.dp,
                                                color = AppTheme.colors.contentWarning
                                            )
                                            .background(AppTheme.colors.contentWarning.copy(.2f))
                                            .clickable {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    val intent =
                                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                    val uri =
                                                        Uri.fromParts("package", packageName, null)
                                                    intent.setData(uri)
                                                    startActivity(intent)
                                                }
                                            }
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.grant_file_access),
                                            modifier = Modifier,
                                            color = AppTheme.colors.contentPrimary,
                                            style = AppTheme.typography.dialogSubtitle
                                        )
                                    }
                                }*/

                                Spacer(Modifier.height(64.dp))
                            }
                        }
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                isNotificationServiceEnabled =
                                    context.isNotificationServiceEnabled()

                                /*scope.launch(Dispatchers.IO) {
                                    needFilePermission =
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                                && !Environment.isExternalStorageManager()
                                }*/
                            }

                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val needPermissions = PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun checkNotificationAccess() {
        val cn = ComponentName(this, GisNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val enabled = flat?.contains(cn.flattenToString()) ?: false

        if (!enabled) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Включите доступ к уведомлениям для GMediaHud", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkUpdates() {
        try {
            UpdateChecker.checkForUpdate(
                this,
                "drauger",
                "GMediaHud",
                object : UpdateChecker.UpdateCallback {
                    override fun onUpdateAvailable(info: UpdateInfo) {
                        updateInfo = info
                    }
                    override fun onNoUpdate() {
                        Toast.makeText(this@MainActivity, "Обновлений нет", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(error: String) {
                        Toast.makeText(this@MainActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Проверка не удалась", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload(info: UpdateInfo) {
        downloadProgress = 0
        UpdateDownloader.download(this, info, object : UpdateDownloader.DownloadCallback {
            override fun onProgress(percent: Int) {
                downloadProgress = percent
            }
            override fun onComplete(apkFile: File) {
                downloadProgress = -1
                UpdateDownloader.installApk(this@MainActivity, apkFile)
            }
            override fun onError(error: String) {
                downloadProgress = -1
                Toast.makeText(this@MainActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
            }
        })
    }
}

private fun Int.toDecimalSecondString() =
    String.format(Locale.US, "%.1f сек", this / 1000.0)

private val sources: List<Int>
    get() = listOf(
        CustomSourceType.LOCAL,
        CustomSourceType.USB,
        CustomSourceType.BT,
        CustomSourceType.FM,
        CustomSourceType.AM,
        CustomSourceType.AUX,
        CustomSourceType.ONLINE,
        CustomSourceType.USB2,
        CustomSourceType.STATION,
        CustomSourceType.NET_NEWS,
        CustomSourceType.NET_VIDEO,
        CustomSourceType.DAB,
//        CustomSourceType.FAVORITE_FM,
//        CustomSourceType.FAVORITE_AM,
        CustomSourceType.FAVORITE_MUSIC,
//        CustomSourceType.AM_SCAN_LIST,
//        CustomSourceType.FM_SCAN_LIST,
    )

private fun Int.getSourceName() = when (this) {
    CustomSourceType.LOCAL -> "LOCAL"
    CustomSourceType.USB -> "USB"
    CustomSourceType.BT -> "BT"
    CustomSourceType.FM -> "FM"
    CustomSourceType.AM -> "AM"
    CustomSourceType.AUX -> "AUX"
    CustomSourceType.ONLINE -> "ONLINE"
    CustomSourceType.USB2 -> "USB2"
    CustomSourceType.STATION -> "STATION"
    CustomSourceType.NET_NEWS -> "NET_NEWS"
    CustomSourceType.NET_VIDEO -> "NET_VIDEO"
    CustomSourceType.DAB -> "DAB"
//    CustomSourceType.FAVORITE_FM -> "FAV_FM"
//    CustomSourceType.FAVORITE_AM -> "FAV_AM"
    CustomSourceType.FAVORITE_MUSIC -> "FAV_MUSIC"
//    CustomSourceType.AM_SCAN_LIST -> "AM_SCAN_LIST"
//    CustomSourceType.FM_SCAN_LIST -> "FM_SCAN_LIST"
    else -> "UNKNOWN"
}
