package ru.toinet.tgws

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

class ProxyService : Service() {
    private var proxyCore: ProxyCore? = null

    companion object {
        const val ACTION_STOP = "ru.toinet.tgws.STOP"
        private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 100)
        val logs = _logs.asSharedFlow()

        var isRunning = false
            private set

        fun start(context: Context, host: String, port: Int, dcMappings: Map<Int, String>) {
            val intent = Intent(context, ProxyService::class.java).apply {
                putExtra("host", host)
                putExtra("port", port)
                val mappingList = dcMappings.map { "${it.key}:${it.value}" }.toTypedArray()
                putExtra("dcMappings", mappingList)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProxyService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra("host") ?: "127.0.0.1"
        val port = intent?.getIntExtra("port", 1480) ?: 1480
        val mappingArray = intent?.getStringArrayExtra("dcMappings") ?: emptyArray()
        val dcMappings = mappingArray.associate {
            val parts = it.split(":")
            parts[0].toInt() to parts[1]
        }

        createNotificationChannel()
        
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ProxyService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "proxy_channel")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, host, port))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_stop), stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.action_open), openPendingIntent)
            .build()

        startForeground(1, notification)

        proxyCore?.stop()
        proxyCore = ProxyCore(host, port, dcMappings) { msg ->
            _logs.tryEmit(msg)
        }
        proxyCore?.start()
        isRunning = true

        return START_STICKY
    }

    override fun onDestroy() {
        proxyCore?.stop()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "proxy_channel",
                getString(R.string.proxy_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}