package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PacelyAppContainer
import com.example.R
import com.example.data.model.TrackingStatus
import com.example.tracking.GeoMath
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        val trackingManager = PacelyAppContainer.get(applicationContext).trackingManager
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    trackingManager.onLocationUpdate(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trackingManager = PacelyAppContainer.get(applicationContext).trackingManager

        when (intent?.action) {
            ACTION_START -> {
                ensureForeground()
                requestUpdates()
                observeForNotificationUpdates()
            }
            ACTION_PAUSE -> {
                trackingManager.pauseTracking()
                updateNotification()
            }
            ACTION_RESUME -> {
                trackingManager.resumeTracking()
                updateNotification()
            }
            ACTION_STOP -> {
                stopTrackingService()
            }
            ACTION_WARM_GPS -> {
                requestUpdates()
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        if (isForegroundStarted) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
    }

    private fun requestUpdates() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun observeForNotificationUpdates() {
        notificationJob?.cancel()
        val trackingManager = PacelyAppContainer.get(applicationContext).trackingManager
        notificationJob = serviceScope.launch {
            trackingManager.uiState.collect { state ->
                if (isForegroundStarted && state.status != TrackingStatus.IDLE) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }
    }

    private fun updateNotification() {
        if (!isForegroundStarted) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = PacelyAppContainer.get(applicationContext).trackingManager.uiState.value
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusPrefix = when {
            state.status == TrackingStatus.PAUSED && state.isAutoPaused -> "Auto-Paused"
            state.status == TrackingStatus.PAUSED -> "Paused"
            else -> "Active"
        }
        val title = "Pacely • $statusPrefix ${state.activityType.label}"
        val content = "${GeoMath.formatDistanceKm(state.distanceMeters)} km  •  ${GeoMath.formatElapsedTime(state.elapsedMillis)}  •  ${GeoMath.formatPace(state.avgPaceSecPerKm)} /km"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.status == TrackingStatus.TRACKING) {
            val pauseIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePending = PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.action_pause), pausePending)
        } else if (state.status == TrackingStatus.PAUSED) {
            val resumeIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePending = PendingIntent.getService(
                this,
                2,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.action_resume), resumePending)
        }

        return builder.build()
    }

    private fun stopTrackingService() {
        notificationJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_tracking_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "pacely_tracking_channel"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_START = "com.example.pacely.START_TRACKING"
        const val ACTION_PAUSE = "com.example.pacely.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.example.pacely.RESUME_TRACKING"
        const val ACTION_STOP = "com.example.pacely.STOP_TRACKING"
        const val ACTION_WARM_GPS = "com.example.pacely.WARM_GPS"

        fun startService(context: Context, action: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                this.action = action
            }
            if (action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
