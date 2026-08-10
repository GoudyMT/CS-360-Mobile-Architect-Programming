package com.goudy.inventoryapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.User;
import com.goudy.inventoryapp.model.UserStatus;

import java.util.List;

/**
 * Reads and writes for user accounts - login lookups, registration, and the account
 * management Leadership handles. Username lookups hit the unique index.
 */
@Dao
public interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User findByUsername(String username);

    // Guards duplicate usernames at registration.
    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    int countByUsername(String username);

    // Leadership's pending-approval queue.
    @Query("SELECT * FROM users WHERE status = :status ORDER BY username")
    LiveData<List<User>> observeByStatus(UserStatus status);

    @Insert
    long insert(User user);

    @Update
    int update(User user);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(long id);

    // Sets a new password and clears the must-change flag once the user picks their own.
    @Query("UPDATE users SET passwordHash = :passwordHash, mustChangePassword = 0 WHERE id = :id")
    void setPassword(long id, String passwordHash);

    // Provisioned accounts Leadership manages: active plus disabled, name-ordered.
    @Query("SELECT * FROM users WHERE status = :active OR status = :disabled ORDER BY lastName, firstName")
    LiveData<List<User>> observeAccounts(UserStatus active, UserStatus disabled);

    // Last-admin guard: how many leadership accounts are still active.
    @Query("SELECT COUNT(*) FROM users WHERE role = :role AND status = :status")
    int countByRoleAndStatus(Role role, UserStatus status);

    @Query("UPDATE users SET status = :status WHERE id = :id")
    void setStatus(long id, UserStatus status);

    @Query("UPDATE users SET role = :role WHERE id = :id")
    void setRole(long id, Role role);

    // Reset: set the temporary password and force a change at next login.
    @Query("UPDATE users SET passwordHash = :passwordHash, mustChangePassword = 1 WHERE id = :id")
    void resetPassword(long id, String passwordHash);
}
