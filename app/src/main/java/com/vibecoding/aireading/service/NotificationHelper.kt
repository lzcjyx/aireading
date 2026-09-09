package com.vibecoding.aireading.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.vibecoding.aireading.R
import com.vibecoding.aireading.ui.ReadActivity

object NotificationHelper {

    const val CHANNEL_ID = "ai_reading_playback_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PLAY = "com.vibecoding.aireading.ACTION_PLAY"
    const val ACTION_PAUSE = "com.vibecoding.aireading.ACTION_PAUSE"
    const val ACTION_PREV = "com.vibecoding.aireading.ACTION_PREV"
    const val ACTION_NEXT = "com.vibecoding.aireading.ACTION_NEXT"
    const val ACTION_STOP = "com.vibecoding.aireading.ACTION_STOP"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.reading_service_channel)
            val desc = context.getString(R.string.reading_service_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = desc
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        bookTitle: String,
        chapterTitle: String,
        sentenceText: String,
        isPlaying: Boolean,
        mediaSessionToken: MediaSessionCompat.Token?
    ): Notification {
        val clickIntent = Intent(context, ReadActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = PendingIntent.getActivity(context, 0, clickIntent, flags)

        val prevIntent = PendingIntent.getService(
            context, 1,
            Intent(context, ReadAloudService::class.java).apply { action = ACTION_PREV },
            flags
        )
        val playPauseIntent = PendingIntent.getService(
            context, 2,
            Intent(context, ReadAloudService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            flags
        )
        val nextIntent = PendingIntent.getService(
            context, 3,
            Intent(context, ReadAloudService::class.java).apply { action = ACTION_NEXT },
            flags
        )
        val stopIntent = PendingIntent.getService(
            context, 4,
            Intent(context, ReadAloudService::class.java).apply { action = ACTION_STOP },
            flags
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (isPlaying) context.getString(R.string.pause) else context.getString(R.string.play)

        val style = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        if (mediaSessionToken != null) {
            style.setMediaSession(mediaSessionToken)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_book)
            .setContentTitle(bookTitle)
            .setContentText("$chapterTitle: $sentenceText")
            .setSubText(chapterTitle)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(style)
            .addAction(R.drawable.ic_skip_prev, context.getString(R.string.prev_sentence), prevIntent)
            .addAction(playPauseIcon, playPauseText, playPauseIntent)
            .addAction(R.drawable.ic_skip_next, context.getString(R.string.next_sentence), nextIntent)
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), stopIntent)
            .build()
    }
}
