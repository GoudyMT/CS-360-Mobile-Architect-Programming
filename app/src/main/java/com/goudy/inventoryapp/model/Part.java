package com.goudy.inventoryapp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A single inventory part - the Room row for the inventory table, kept immutable.
 * Status is DERIVED from type and tolerance and never stored, so the reorder rule
 * lives in one place. Indexed on the columns the grid searches and sorts on.
 */
@Entity(
        tableName = "inventory",
        indices = {
                @Index(value = "partNumber", unique = true),
                @Index("name"),
                @Index("system")
        }
)
public class Part {

    @PrimaryKey(autoGenerate = true)
    private final long id;
    @NonNull private final String name;
    @NonNull private final String system;       // associated system
    @NonNull private final String partNumber;   // stock number / SKU
    @NonNull private final String location;     // bay / storage location
    @NonNull private final PartType type;
    private final int quantity;                 // on hand
    private final int authorizedQty;            // allowance parts: authorized on-hand
    private final int lowThreshold;             // consumables: reorder-at level
    private final int versionNumber;            // optimistic lock - first checkout wins, stale writes are rejected

    public Part(long id, @NonNull String name, @NonNull String system, @NonNull String partNumber,
                @NonNull String location, @NonNull PartType type, int quantity, int authorizedQty,
                int lowThreshold, int versionNumber) {
        this.id = id;
        this.name = name;
        this.system = system;
        this.partNumber = partNumber;
        this.location = location;
        this.type = type;
        this.quantity = quantity;
        this.authorizedQty = authorizedQty;
        this.lowThreshold = lowThreshold;
        this.versionNumber = versionNumber;
    }

    public long getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getSystem() { return system; }
    @NonNull public String getPartNumber() { return partNumber; }
    @NonNull public String getLocation() { return location; }
    @NonNull public PartType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getAuthorizedQty() { return authorizedQty; }
    public int getLowThreshold() { return lowThreshold; }
    public int getVersionNumber() { return versionNumber; }

    /**
     * Derives the stock status from quantity and tolerance. Allowance parts are low
     * below their authorized on-hand; consumables are low at or below their reorder
     * threshold. Marked @Ignore so Room treats it as logic, not a stored column.
     */
    @Ignore
    public StockStatus getStatus() {
        if (quantity <= 0) {
            return StockStatus.OUT;
        }
        if (type == PartType.ALLOWANCE) {
            return quantity < authorizedQty ? StockStatus.LOW : StockStatus.IN_STOCK;
        }
        return quantity <= lowThreshold ? StockStatus.LOW : StockStatus.IN_STOCK;
    }
}
