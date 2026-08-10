package com.goudy.inventoryapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.goudy.inventoryapp.model.AuditEntry;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.PartSystem;
import com.goudy.inventoryapp.model.User;

/**
 * The app's single Room database - inventory, users, systems, and the audit trail. On the very
 * first run it seeds a working dataset so the app is usable and there is always an admin to
 * approve others. The seed runs synchronously as the file is created, so the first query
 * already sees it.
 */
@Database(
        entities = {Part.class, User.class, PartSystem.class, AuditEntry.class},
        version = 1,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class InventoryDatabase extends RoomDatabase {

    public abstract InventoryDao inventoryDao();

    public abstract UserDao userDao();

    public abstract SystemDao systemDao();

    public abstract AuditDao auditDao();

    // The generic first-time password every provisioned account gets; the user changes it on first login.
    public static final String TEMP_PASSWORD = "Flightline1";

    private static volatile InventoryDatabase instance;

    public static InventoryDatabase get(Context context) {
        if (instance == null) {
            synchronized (InventoryDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    InventoryDatabase.class, "inventory.db")
                            .addCallback(SEED)
                            .build();
                }
            }
        }
        return instance;
    }

    // Runs once, when the database file is first created. Seeds directly and synchronously so the
    // very first query already sees the data - a background seed would race the first read.
    private static final Callback SEED = new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            insertSystem(db, "PAR", "Precision Approach Radar");
            insertSystem(db, "DASR", "Digital Airport Surveillance Radar");
            insertSystem(db, "TACAN", "Tactical Air Navigation");

            for (Part part : SampleInventory.parts()) {
                insertPart(db, part);
            }

            // Bootstrap admin so the first requested accounts have someone to approve them.
            insertUser(db, "System", "Administrator", "N/A", "admin@nas-whidbey.example",
                    "NAS Whidbey Island", "admin", PasswordHasher.hash("flightline"),
                    "LEADERSHIP", "ACTIVE", false);
            // A freshly provisioned technician - signs in with the temporary password, then must set their own.
            insertUser(db, "Jordan", "Rivera", "AM2 (E-5)", "jordan.rivera@nas-whidbey.example",
                    "NAS Whidbey Island", "jordan.rivera", PasswordHasher.hash(TEMP_PASSWORD),
                    "TECHNICIAN", "ACTIVE", true);
            // A pending request awaiting Leadership - no username or password until approved.
            insertUser(db, "Alex", "Chen", "LS2 (E-5)", "alex.chen@nas-whidbey.example",
                    "NAS Whidbey Island", null, null, null, "PENDING", false);
        }
    };

    private static void insertSystem(SupportSQLiteDatabase db, String code, String name) {
        ContentValues values = new ContentValues();
        values.put("code", code);
        values.put("name", name);
        db.insert("systems", SQLiteDatabase.CONFLICT_ABORT, values);
    }

    private static void insertPart(SupportSQLiteDatabase db, Part part) {
        ContentValues values = new ContentValues();
        values.put("name", part.getName());
        values.put("system", part.getSystem());
        values.put("partNumber", part.getPartNumber());
        values.put("location", part.getLocation());
        values.put("type", part.getType().name());
        values.put("quantity", part.getQuantity());
        values.put("authorizedQty", part.getAuthorizedQty());
        values.put("lowThreshold", part.getLowThreshold());
        values.put("versionNumber", part.getVersionNumber());
        db.insert("inventory", SQLiteDatabase.CONFLICT_ABORT, values);
    }

    private static void insertUser(SupportSQLiteDatabase db, String firstName, String lastName,
            String rate, String email, String station, String username, String passwordHash,
            String role, String status, boolean mustChangePassword) {
        ContentValues values = new ContentValues();
        values.put("firstName", firstName);
        values.put("lastName", lastName);
        values.put("rate", rate);
        values.put("email", email);
        values.put("station", station);
        values.put("username", username);           // null for a pending request
        values.put("passwordHash", passwordHash);   // null until provisioned
        values.put("role", role);                    // null until assigned at approval
        values.put("status", status);
        values.put("mustChangePassword", mustChangePassword ? 1 : 0);
        db.insert("users", SQLiteDatabase.CONFLICT_ABORT, values);
    }
}
