package com.goudy.inventoryapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.goudy.inventoryapp.model.AuditEntry;

import java.util.List;

/**
 * Writes and reads for the audit trail. History for one item comes back newest-first,
 * ordered off the indexed timestamp column.
 */
@Dao
public interface AuditDao {

    @Insert
    long insert(AuditEntry entry);

    @Query("SELECT * FROM audit_log WHERE itemId = :itemId ORDER BY timestamp DESC")
    LiveData<List<AuditEntry>> observeForItem(long itemId);
}
