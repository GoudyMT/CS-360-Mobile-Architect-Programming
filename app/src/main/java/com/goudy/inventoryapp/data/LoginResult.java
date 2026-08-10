package com.goudy.inventoryapp.data;

import com.goudy.inventoryapp.model.User;

/**
 * The outcome of a sign-in attempt. On SUCCESS the user is attached (for the role and the
 * must-change-password check); a failure carries its reason so the screen can explain it.
 */
public class LoginResult {

    public enum Outcome { SUCCESS, INVALID_CREDENTIALS, DISABLED }

    private final Outcome outcome;
    private final User user;   // set only on SUCCESS

    private LoginResult(Outcome outcome, User user) {
        this.outcome = outcome;
        this.user = user;
    }

    public static LoginResult success(User user) {
        return new LoginResult(Outcome.SUCCESS, user);
    }

    public static LoginResult of(Outcome outcome) {
        return new LoginResult(outcome, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public User getUser() {
        return user;
    }
}
