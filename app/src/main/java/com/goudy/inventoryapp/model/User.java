package com.goudy.inventoryapp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A person in the system across the whole account lifecycle. A pending request holds the
 * requester's details with no username or password yet; those,
 * plus the role, are filled in when Leadership approves and the account is provisioned. The
 * password is only ever a salted hash. Username is unique once assigned.
 */
@Entity(tableName = "users", indices = {@Index(value = "username", unique = true)})
public class User {

    @PrimaryKey(autoGenerate = true)
    private final long id;
    @NonNull private final String firstName;
    @NonNull private final String lastName;
    @NonNull private final String rate;        // rate / rank - the basis for the assigned role
    @NonNull private final String email;       // account notifications and report delivery
    @NonNull private final String station;
    private final String username;             // generated as firstname.lastname at approval; null while pending
    private final String passwordHash;         // salted hash of the temporary, then chosen, password; null while pending
    private final Role role;                   // assigned at approval; null while pending
    @NonNull private final UserStatus status;
    private final boolean mustChangePassword;  // true right after provisioning, false once the user sets their own

    public User(long id, @NonNull String firstName, @NonNull String lastName, @NonNull String rate,
                @NonNull String email, @NonNull String station, String username, String passwordHash,
                Role role, @NonNull UserStatus status, boolean mustChangePassword) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.rate = rate;
        this.email = email;
        this.station = station;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
    }

    public long getId() { return id; }
    @NonNull public String getFirstName() { return firstName; }
    @NonNull public String getLastName() { return lastName; }
    @NonNull public String getRate() { return rate; }
    @NonNull public String getEmail() { return email; }
    @NonNull public String getStation() { return station; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    @NonNull public UserStatus getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
}
