package com.goudy.inventoryapp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A system a part belongs to. Its own table so Supply and
 * Leadership can add new systems and the New Part dropdown reads the live list
 * instead of a hardcoded array. Code is unique - one row per system.
 */
@Entity(tableName = "systems", indices = {@Index(value = "code", unique = true)})
public class PartSystem {

    @PrimaryKey(autoGenerate = true)
    private final long id;
    @NonNull private final String code;   // short tag shown on cards
    @NonNull private final String name;   // full name

    public PartSystem(long id, @NonNull String code, @NonNull String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public long getId() { return id; }
    @NonNull public String getCode() { return code; }
    @NonNull public String getName() { return name; }
}
