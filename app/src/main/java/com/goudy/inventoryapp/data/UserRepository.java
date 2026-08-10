package com.goudy.inventoryapp.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;

import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.User;
import com.goudy.inventoryapp.model.UserStatus;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Account operations - sign-in and password changes - kept off the Activities and off the
 * main thread. Each call runs its database work on a background thread and delivers the
 * result back on the main thread, so the UI reacts without ever blocking on the database.
 */
public class UserRepository {

    private final UserDao userDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UserRepository(Context context) {
        this.userDao = InventoryDatabase.get(context).userDao();
    }

    /** Verifies the username and password, reporting why a sign-in fails. */
    public void login(String username, String password, Consumer<LoginResult> callback) {
        executor.execute(() -> {
            User user = userDao.findByUsername(username);
            LoginResult result;
            if (user == null || user.getPasswordHash() == null
                    || !PasswordHasher.matches(password, user.getPasswordHash())) {
                // One message whether the account is unknown or the password is wrong - never reveal which.
                result = LoginResult.of(LoginResult.Outcome.INVALID_CREDENTIALS);
            } else if (user.getStatus() != UserStatus.ACTIVE) {
                result = LoginResult.of(LoginResult.Outcome.DISABLED);
            } else {
                result = LoginResult.success(user);
            }
            mainHandler.post(() -> callback.accept(result));
        });
    }

    /** Replaces the account's password and clears the must-change flag. */
    public void changePassword(long userId, String newPassword, Runnable onDone) {
        executor.execute(() -> {
            userDao.setPassword(userId, PasswordHasher.hash(newPassword));
            mainHandler.post(onDone);
        });
    }

    /** Loads one account off the main thread - used to show who is signed in. */
    public void getUser(long userId, Consumer<User> callback) {
        executor.execute(() -> {
            User user = userDao.findById(userId);
            mainHandler.post(() -> callback.accept(user));
        });
    }

    /** Saves an access request as a pending account; Leadership provisions it on approval. */
    public void register(String firstName, String lastName, String rate, String email,
            String station, Runnable onDone) {
        executor.execute(() -> {
            userDao.insert(new User(0, firstName, lastName, rate, email, station,
                    null, null, null, UserStatus.PENDING, false));
            mainHandler.post(onDone);
        });
    }

    /** The Leadership approval queue - accounts awaiting a decision. */
    public LiveData<List<User>> observePending() {
        return userDao.observeByStatus(UserStatus.PENDING);
    }

    /**
     * Approves a request: generates firstname.lastname (numbered on collision), sets the generic
     * temp password (forced change on first login), assigns the role, and activates the account.
     * Delivers the new username so it can be shown and "emailed" to the requester.
     */
    public void approve(User request, Role role, Consumer<String> onDone) {
        executor.execute(() -> {
            String username = uniqueUsername(request.getFirstName(), request.getLastName());
            userDao.update(new User(request.getId(), request.getFirstName(), request.getLastName(),
                    request.getRate(), request.getEmail(), request.getStation(),
                    username, PasswordHasher.hash(InventoryDatabase.TEMP_PASSWORD), role,
                    UserStatus.ACTIVE, true));
            mainHandler.post(() -> onDone.accept(username));
        });
    }

    /** Denies a pending request; the row is kept as a record, marked denied. */
    public void deny(User request, Runnable onDone) {
        executor.execute(() -> {
            userDao.update(new User(request.getId(), request.getFirstName(), request.getLastName(),
                    request.getRate(), request.getEmail(), request.getStation(),
                    null, null, null, UserStatus.DENIED, false));
            mainHandler.post(onDone);
        });
    }

    /** Provisioned accounts (active + disabled) for Leadership's management list. */
    public LiveData<List<User>> observeAccounts() {
        return userDao.observeAccounts(UserStatus.ACTIVE, UserStatus.DISABLED);
    }

    /**
     * Enables or disables an account. Disabling the last active Leadership account is refused - the
     * check runs on the database thread so the count can't shift under it. Reports whether it applied.
     */
    public void setEnabled(User user, boolean enable, Consumer<Boolean> onResult) {
        executor.execute(() -> {
            boolean allowed = enable || !isLastActiveLeadership(user);
            if (allowed) {
                userDao.setStatus(user.getId(), enable ? UserStatus.ACTIVE : UserStatus.DISABLED);
            }
            mainHandler.post(() -> onResult.accept(allowed));
        });
    }

    /** Changes a role; refuses to demote the last active Leadership account. Reports whether it applied. */
    public void changeRole(User user, Role role, Consumer<Boolean> onResult) {
        executor.execute(() -> {
            boolean allowed = role == Role.LEADERSHIP || !isLastActiveLeadership(user);
            if (allowed) {
                userDao.setRole(user.getId(), role);
            }
            mainHandler.post(() -> onResult.accept(allowed));
        });
    }

    /** Resets an account to the shared temporary password and forces a change at next login. */
    public void resetPassword(User user, Runnable onDone) {
        executor.execute(() -> {
            userDao.resetPassword(user.getId(), PasswordHasher.hash(InventoryDatabase.TEMP_PASSWORD));
            mainHandler.post(onDone);
        });
    }

    /** True when this is the only active Leadership account, so it can't be disabled or demoted. */
    private boolean isLastActiveLeadership(User user) {
        return user.getRole() == Role.LEADERSHIP
                && user.getStatus() == UserStatus.ACTIVE
                && userDao.countByRoleAndStatus(Role.LEADERSHIP, UserStatus.ACTIVE) <= 1;
    }

    /** firstname.lastname, lowercased; on collision appends the lowest free number. */
    private String uniqueUsername(String firstName, String lastName) {
        String base = (firstName + "." + lastName).toLowerCase(Locale.US).replaceAll("[^a-z0-9.]", "");
        if (userDao.countByUsername(base) == 0) {
            return base;
        }
        int n = 1;
        while (userDao.countByUsername(base + n) > 0) {
            n++;
        }
        return base + n;
    }
}
