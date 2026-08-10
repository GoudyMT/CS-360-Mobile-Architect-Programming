package com.goudy.inventoryapp.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;

import com.goudy.inventoryapp.model.AuditAction;
import com.goudy.inventoryapp.model.AuditEntry;
import com.goudy.inventoryapp.model.Part;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The one place the app talks to the inventory table. Reads come back as LiveData (so the grid
 * observes them); one-shot loads, the scan lookup, and every write run on a background thread and
 * deliver back on the main thread, so the UI never blocks on the database.
 */
public class InventoryRepository {

    private final InventoryDatabase database;
    private final InventoryDao inventoryDao;
    private final AuditDao auditDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public InventoryRepository(Context context) {
        this.database = InventoryDatabase.get(context);
        this.inventoryDao = database.inventoryDao();
        this.auditDao = database.auditDao();
    }

    public LiveData<List<Part>> observeAll() {
        return inventoryDao.observeAll();
    }

    public LiveData<List<Part>> search(String query) {
        return inventoryDao.search(query);
    }

    /** Loads one part off the main thread - for the detail screen. */
    public void getPart(long id, Consumer<Part> callback) {
        executor.execute(() -> {
            Part part = inventoryDao.findById(id);
            mainHandler.post(() -> callback.accept(part));
        });
    }

    /** The first part on hand - a stand-in for scanning a known item. */
    public void firstPart(Consumer<Part> callback) {
        executor.execute(() -> {
            List<Part> all = inventoryDao.getAll();
            Part first = all.isEmpty() ? null : all.get(0);
            mainHandler.post(() -> callback.accept(first));
        });
    }

    /**
     * The scan fork: an exact SKU returns FOUND; otherwise similar SKUs are surfaced as NEAR (the
     * typo guard), and only a true miss returns NONE - so every add-new flows through here.
     */
    public void lookUp(String partNumber, Consumer<LookupResult> callback) {
        executor.execute(() -> {
            Part exact = inventoryDao.findByPartNumber(partNumber);
            LookupResult result;
            if (exact != null) {
                result = LookupResult.found(exact);
            } else {
                List<Part> near = nearMatches(partNumber, inventoryDao.getAll());
                result = near.isEmpty() ? LookupResult.none() : LookupResult.near(near);
            }
            mainHandler.post(() -> callback.accept(result));
        });
    }

    /** Receiving: adds the given amount to a part's on-hand quantity. */
    public void receive(Part part, int amount, Runnable onDone) {
        executor.execute(() -> {
            inventoryDao.update(withQuantity(part, part.getQuantity() + amount));
            mainHandler.post(onDone);
        });
    }

    /**
     * Checks one unit out atomically: decrements the quantity with an optimistic-lock check and writes
     * the audit row in a single transaction, so the count and its record can never drift. Delivers the
     * fresh part on success (so the caller can see the new stock level), or null if the row changed
     * underneath the transaction or is out of stock - a stale checkout the caller should retry.
     */
    public void checkout(Part part, long userId, String jobNumber, Consumer<Part> callback) {
        executor.execute(() -> {
            Part updated = database.runInTransaction(() -> {
                if (inventoryDao.decrementForCheckout(part.getId(), part.getVersionNumber()) == 0) {
                    return null;
                }
                auditDao.insert(new AuditEntry(0, part.getId(), userId, AuditAction.CHECKOUT,
                        -1, jobNumber, System.currentTimeMillis()));
                return inventoryDao.findById(part.getId());
            });
            mainHandler.post(() -> callback.accept(updated));
        });
    }

    public void insert(Part part) {
        executor.execute(() -> inventoryDao.insert(part));
    }

    public void update(Part part) {
        executor.execute(() -> inventoryDao.update(part));
    }

    public void delete(Part part) {
        executor.execute(() -> inventoryDao.delete(part));
    }

    private static Part withQuantity(Part p, int quantity) {
        return new Part(p.getId(), p.getName(), p.getSystem(), p.getPartNumber(), p.getLocation(),
                p.getType(), quantity, p.getAuthorizedQty(), p.getLowThreshold(), p.getVersionNumber());
    }

    /** Parts whose SKU is close to the query - the near-match guard against typo duplicates. */
    private static List<Part> nearMatches(String query, List<Part> parts) {
        List<Part> matches = new ArrayList<>();
        String q = query.trim().toUpperCase(Locale.US);
        if (q.length() < 3) {
            return matches;
        }
        for (Part part : parts) {
            String number = part.getPartNumber().toUpperCase(Locale.US);
            if (number.equals(q)) {
                continue;
            }
            if (number.contains(q) || q.contains(number) || sharedPrefix(number, q) >= 4) {
                matches.add(part);
            }
        }
        return matches;
    }

    private static int sharedPrefix(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        int i = 0;
        while (i < limit && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
