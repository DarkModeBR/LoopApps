package com.loop.explorer.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.loop.explorer.R
import com.loop.explorer.browse.MainActivity
import com.loop.explorer.privileged.ShizukuGateway
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the HTTP server running, also with the explorer closed, until it is turned off. Android
 * asks for a notification while it runs. It does not come back after the device restarts.
 */
class ServerService : Service() {
    private var server: HttpServer? = null
    private var gateway: ShizukuGateway? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null && !startServer()) {
            // Android closes the app when a service started this way never shows its notification,
            // even if it stops at once; so it is shown and removed before stopping.
            runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startServer(): Boolean {
        val shizuku = ShizukuGateway(this)
        gateway = shizuku
        val api = WebApi(this, Shell(shizuku, cacheDir))
        val http = HttpServer(api::handle)
        if (!http.start(PORTS)) return false
        urls = addresses().map { "http://$it:${http.port}" }.ifEmpty { listOf("http://127.0.0.1:${http.port}") }
        // When Android brings the service back by itself, it may refuse the notification; then it stays off.
        val shown = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType()) }.isSuccess
        if (!shown) {
            http.stop()
            urls = emptyList()
            return false
        }
        server = http
        running = true
        wifiLock = runCatching {
            @Suppress("DEPRECATION")
            getSystemService(WifiManager::class.java)?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "loop-explorer-server")?.apply { acquire() }
        }.getOrNull()
        publish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
        gateway?.release()
        runCatching { wifiLock?.release() }
        running = false
        urls = emptyList()
        Clients.clear()
        publish()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.server_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_server)
            .setContentTitle(getString(R.string.server_running))
            .setContentText(urls.firstOrNull())
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.server_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "server"
        private const val NOTIFICATION_ID = 7
        private val PORTS = 8080..8090
        private val main = Handler(Looper.getMainLooper())
        private val listeners = CopyOnWriteArrayList<() -> Unit>()

        @Volatile
        var running = false
            private set

        @Volatile
        var urls: List<String> = emptyList()
            private set

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }

        fun addListener(listener: () -> Unit) {
            listeners += listener
        }

        fun removeListener(listener: () -> Unit) {
            listeners -= listener
        }

        private fun publish() = main.post { listeners.forEach { it() } }

        /** The addresses of this device on the home network, Wi-Fi or cable. */
        fun addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { it.hostAddress.orEmpty() }
                .filter { it.isNotEmpty() }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
