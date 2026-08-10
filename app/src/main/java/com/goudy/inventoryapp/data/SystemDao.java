package com.goudy.inventoryapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.goudy.inventoryapp.model.PartSystem;

import java.util.List;

/**
 * Reads and writes for the systems catalog. The New Part dropdown observes this list, so
 * a system Supply or Leadership adds shows up there without any code change.
 */
@Dao
public interface SystemDao {

    @Query("SELECT * FROM systems ORDER BY code")
    LiveData<List<PartSystem>> observeAll();

    // Backs the "system-in-use" check before a system can be removed.
    @Query("SELECT COUNT(*) FROM inventory WHERE system = :code")
    int countPartsInSystem(String code);

    @Insert
    long insert(PartSystem system);

    @Delete
    int delete(PartSystem system);
}
