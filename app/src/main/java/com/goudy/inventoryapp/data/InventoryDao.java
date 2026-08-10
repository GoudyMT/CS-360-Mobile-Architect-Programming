package com.goudy.inventoryapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.goudy.inventoryapp.model.Part;

import java.util.List;

/**
 * Reads and writes for the inventory table. Reads return LiveData so the grid refreshes
 * itself when the data changes; the search runs in SQL against the indexed columns, so it
 * stays fast on a large table instead of loading everything into memory to filter.
 */
@Dao
public interface InventoryDao {

    // Reactive: Room re-runs this only when the inventory table changes and pushes the fresh list.
    @Query("SELECT * FROM inventory ORDER BY name")
    LiveData<List<Part>> observeAll();

    // DB-side search on the indexed name / partNumber columns.
    @Query("SELECT * FROM inventory WHERE name LIKE '%' || :query || '%' "
            + "OR partNumber LIKE '%' || :query || '%' ORDER BY name")
    LiveData<List<Part>> search(String query);

    // Single-row lookups for the detail screen and the scanner (called off the main thread).
    @Query("SELECT * FROM inventory WHERE id = :id LIMIT 1")
    Part findById(long id);

    @Query("SELECT * FROM inventory WHERE partNumber = :partNumber LIMIT 1")
    Part findByPartNumber(String partNumber);

    // A one-time snapshot (not LiveData) for the scan's near-match search, run off the main thread.
    @Query("SELECT * FROM inventory")
    List<Part> getAll();

    // Optimistic-locked checkout decrement: succeeds (returns 1) only if the row is unchanged and in stock.
    @Query("UPDATE inventory SET quantity = quantity - 1, versionNumber = versionNumber + 1 "
            + "WHERE id = :id AND versionNumber = :expectedVersion AND quantity > 0")
    int decrementForCheckout(long id, int expectedVersion);

    @Insert
    long insert(Part part);

    @Update
    int update(Part part);

    @Delete
    int delete(Part part);
}
