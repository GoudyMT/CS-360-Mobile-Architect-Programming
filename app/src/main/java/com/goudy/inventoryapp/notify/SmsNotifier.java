package com.goudy.inventoryapp.notify;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.goudy.inventoryapp.R;
import com.goudy.inventoryapp.model.Part;

import java.util.List;

/**
 * Sends alerts as a text message from the device. If SEND_SMS isn't granted it does nothing, so the
 * rest of the app keeps working without texts. A server build would instead POST the alert to a
 * push/email backend to reach off-site supply staff - the call sites here would not change.
 */
public class SmsNotifier implements Notifier {

    // Supply desk line the alerts go to. A real deployment would make this configurable per station.
    private static final String ALERT_NUMBER = "15555215556";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SmsNotifier(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void lowStock(Part part) {
        if (!NotificationPrefs.lowStockAlertsOn(context)) {
            return;
        }
        send(context.getString(R.string.alert_low_stock_sms,
                part.getName(), part.getPartNumber(), part.getQuantity()));
    }

    /** Fires a canned message so the SMS path can be verified from the Notifications screen. */
    public void sendTest() {
        send(context.getString(R.string.alert_test_sms));
    }

    /** One rollup text for a background sweep: every part currently at or below its reorder level. */
    public void lowStockSummary(List<Part> lowParts) {
        if (lowParts.isEmpty() || !NotificationPrefs.lowStockAlertsOn(context)) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < lowParts.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(lowParts.get(i).getName());
        }
        send(context.getString(R.string.alert_low_stock_summary, lowParts.size(), names.toString()));
    }

    private void send(String message) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SmsManager sms = smsManager();
        sms.sendMultipartTextMessage(ALERT_NUMBER, null, sms.divideMessage(message), null, null);
        // May be called from a background worker, so the confirmation toast is posted to the main thread.
        mainHandler.post(() -> Toast.makeText(context,
                context.getString(R.string.alert_sent_toast, ALERT_NUMBER), Toast.LENGTH_SHORT).show());
    }

    /** getDefault is deprecated but still covers older devices. */
    @SuppressWarnings("deprecation")
    private SmsManager smsManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(SmsManager.class);
        }
        return SmsManager.getDefault();
    }
}
