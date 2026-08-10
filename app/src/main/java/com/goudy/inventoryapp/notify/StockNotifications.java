package com.goudy.inventoryapp.notify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.goudy.inventoryapp.MainInventoryActivity;
import com.goudy.inventoryapp.R;

/**
 * On-device low-stock notification: a short "items found" summary that taps straight into the
 * Low & Out view. This is the alert the person holding the phone sees; the SMS path reaches
 * off-site supply. A server build could replace this with a real push to the right people.
 */
public final class StockNotifications {

    private static final String CHANNEL_ID = "low_stock";
    private static final int NOTIFICATION_ID = 1001;

    private StockNotifications() { }

    // Permission is checked below at runtime; lint can't see through the guard, so it's suppressed.
    @SuppressLint("MissingPermission")
    public static void postLowStock(Context context, int count) {
        if (count <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return; // no permission -> app still works, just no on-device alert
        }
        ensureChannel(context);

        Intent open = new Intent(context, MainInventoryActivity.class)
                .putExtra(MainInventoryActivity.EXTRA_SHOW_LOW_OUT, true)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(context.getString(R.string.notif_low_stock_title))
                .setContentText(context.getString(R.string.notif_low_stock_text, count))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build();
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }

    private static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notif_channel_low_stock),
                NotificationManager.IMPORTANCE_DEFAULT);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
