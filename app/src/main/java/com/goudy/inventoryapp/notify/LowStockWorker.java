package com.goudy.inventoryapp.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.goudy.inventoryapp.data.InventoryDatabase;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.StockStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Background low-stock sweep. WorkManager runs this on a schedule and it survives app close and
 * reboot, so supply gets a text when parts run low even with no one in the app. The alert goes out
 * through the same {@link Notifier} seam as the in-app checks.
 */
public class LowStockWorker extends Worker {

    public LowStockWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!NotificationPrefs.lowStockAlertsOn(context)) {
            return Result.success();
        }
        List<Part> low = new ArrayList<>();
        for (Part part : InventoryDatabase.get(context).inventoryDao().getAll()) {
            if (part.getStatus() != StockStatus.IN_STOCK) {
                low.add(part);
            }
        }
        if (!low.isEmpty()) {
            new SmsNotifier(context).lowStockSummary(low);
            StockNotifications.postLowStock(context, low.size());
        }
        return Result.success();
    }
}
