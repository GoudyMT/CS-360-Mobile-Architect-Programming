package com.goudy.inventoryapp.data;

import androidx.room.TypeConverter;

import com.goudy.inventoryapp.model.AuditAction;
import com.goudy.inventoryapp.model.PartType;
import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.UserStatus;

/**
 * Stores each enum as its name so Room can keep it in a TEXT column, and reads it back
 * the same way. One pair of converters per enum the entities use.
 */
public final class Converters {

    private Converters() { }

    @TypeConverter
    public static String fromPartType(PartType type) { return type == null ? null : type.name(); }

    @TypeConverter
    public static PartType toPartType(String name) { return name == null ? null : PartType.valueOf(name); }

    @TypeConverter
    public static String fromRole(Role role) { return role == null ? null : role.name(); }

    @TypeConverter
    public static Role toRole(String name) { return name == null ? null : Role.valueOf(name); }

    @TypeConverter
    public static String fromUserStatus(UserStatus status) { return status == null ? null : status.name(); }

    @TypeConverter
    public static UserStatus toUserStatus(String name) { return name == null ? null : UserStatus.valueOf(name); }

    @TypeConverter
    public static String fromAuditAction(AuditAction action) { return action == null ? null : action.name(); }

    @TypeConverter
    public static AuditAction toAuditAction(String name) { return name == null ? null : AuditAction.valueOf(name); }
}
