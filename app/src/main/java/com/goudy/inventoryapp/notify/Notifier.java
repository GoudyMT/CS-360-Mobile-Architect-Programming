package com.goudy.inventoryapp.notify;

import com.goudy.inventoryapp.model.Part;

/**
 * Delivers alerts out of the app. One seam so the trigger site never changes: today an on-device
 * text ({@link SmsNotifier}); a server build could swap in a push or email implementation behind it.
 */
public interface Notifier {

    /** Alerts supply that a part has dropped to or below its reorder level. */
    void lowStock(Part part);
}
