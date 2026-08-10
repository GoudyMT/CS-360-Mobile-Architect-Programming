package com.goudy.inventoryapp.notify;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which alerts supply wants, so the toggles survive leaving the screen. Both default on;
 * the low-stock flag also gates whether a low-stock checkout actually texts.
 */
public final class NotificationPrefs {

    private static final String PREFS = "notifications";
    private static final String KEY_LOW_STOCK = "low_stock";
    private static final String KEY_DISCREPANCY = "discrepancy";

    private NotificationPrefs() { }

    public static boolean lowStockAlertsOn(Context context) {
        return prefs(context).getBoolean(KEY_LOW_STOCK, true);
    }

    public static void setLowStockAlerts(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_LOW_STOCK, on).apply();
    }

    public static boolean discrepancyReportsOn(Context context) {
        return prefs(context).getBoolean(KEY_DISCREPANCY, true);
    }

    public static void setDiscrepancyReports(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_DISCREPANCY, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
