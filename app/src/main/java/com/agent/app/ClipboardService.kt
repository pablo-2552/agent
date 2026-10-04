package com.agent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File

class ClipboardService : Service() {

    private lateinit var clipboardManager: ClipboardManager
    private val channelId = "agent_service_channel"
    private val notificationId = 101
    private var lastProcessedTime: Long = 0L

    companion object {
        const val ACTION_STOP = "com.agent.app.ACTION_STOP"
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            val currentTime = System.currentTimeMillis()

            if (text.isNotBlank() && (currentTime - lastProcessedTime > 1200)) {
                lastProcessedTime = currentTime
                processClipboardText(text)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("AI Kod Dinleyici Aktif"))

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun processClipboardText(text: String) {
        val regex = Regex(
            """\[AGENT_ACTION:\s*([A-Za-z_]+)\]\s*\[TARGET_PATH:\s*([^\]]+)\]\s*\[CONTENT_START\]([\s\S]*?)\[CONTENT_END\]"""
        )

        val matches = regex.findAll(text).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val action = match.groupValues[1].trim().uppercase()
                val targetPath = match.groupValues[2].trim()
                val content = match.groupValues[3]

                // Sistem talimatının kendisi kopyalandığında sahte dosya açmasını engelle
                if (targetPath.contains("ornek_dosya") || targetPath.contains("{")) continue

                try {
                    val file = File(targetPath)
                    file.parentFile?.mkdirs()

                    when (action) {
                        "WRITE" -> {
                            if (file.exists()) {
                                val currentContent = file.readText()
                                if (currentContent == content) {
                                    handleResult("Zaten güncel: ${file.name}")
                                } else {
                                    file.writeText(content)
                                    handleResult("Güncellendi: ${file.name}")
                                }
                            } else {
                                file.writeText(content)
                                handleResult("Oluşturuldu: ${file.name}")
                            }
                        }
                        "APPEND" -> {
                            file.appendText(content)
                            handleResult("Eklendi: ${file.name}")
                        }
                        "DELETE" -> {
                            if (file.exists()) {
                                file.delete()
                                handleResult("Silindi: ${file.name}")
                            } else {
                                handleResult("Dosya zaten yok: ${file.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    showToast("Hata: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun handleResult(message: String) {
        showToast(message)
        updateNotification("Son: $message")
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, buildNotification(status))
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Agent Dinleme Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ClipboardService::class.java).apply {
            action = ACTION_STOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Agent")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
