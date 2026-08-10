package com.goudy.inventoryapp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One line in the audit trail - who moved what stock, and why. Written together with
 * the quantity change in a single transaction so the count and its record never drift.
 * Append-only: indexed (not hard foreign-keyed) so history is kept even after a part
 * or user is removed, and deletes stay simple.
 */
@Entity(
        tableName = "audit_log",
        indices = {@Index("itemId"), @Index("userId"), @Index("timestamp")}
)
public class AuditEntry {

    @PrimaryKey(autoGenerate = true)
    private final long id;
    private final long itemId;             // the inventory row affected
    private final long userId;             // who did it
    @NonNull private final AuditAction action;
    private final int quantityChange;      // signed: -1 on checkout, +N on receive
    private final String jobNumber;        // checkout context; null on receive
    private final long timestamp;          // epoch millis

    public AuditEntry(long id, long itemId, long userId, @NonNull AuditAction action,
                      int quantityChange, String jobNumber, long timestamp) {
        this.id = id;
        this.itemId = itemId;
        this.userId = userId;
        this.action = action;
        this.quantityChange = quantityChange;
        this.jobNumber = jobNumber;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public long getItemId() { return itemId; }
    public long getUserId() { return userId; }
    @NonNull public AuditAction getAction() { return action; }
    public int getQuantityChange() { return quantityChange; }
    public String getJobNumber() { return jobNumber; }
    public long getTimestamp() { return timestamp; }
}
