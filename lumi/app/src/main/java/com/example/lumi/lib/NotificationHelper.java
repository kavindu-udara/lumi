package com.example.lumi.lib;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.lumi.R;
import com.example.lumi.activities.MainActivity;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "lumi_broadcasts";
    private static final String CHANNEL_NAME = "Lumi broadcasts";
    private static final int NOTIFICATION_ID_BASE = 10000;

    private NotificationHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Broadcast messages from Lumi");
        channel.enableVibration(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    public static boolean show(Context context, String notificationId, String title, String body) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;

        Intent launchIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + Math.abs(notificationId.hashCode() % 100000),
                builder.build()
        );
        return true;
    }
}
