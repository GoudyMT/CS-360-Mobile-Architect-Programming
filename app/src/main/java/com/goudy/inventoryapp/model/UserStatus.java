package com.goudy.inventoryapp.model;

/** Account lifecycle - a requested account stays PENDING until Leadership approves it. */
public enum UserStatus {
    PENDING,    // requested, awaiting approval - cannot sign in yet
    ACTIVE,     // approved - can sign in
    DISABLED,   // access revoked
    DENIED      // request rejected by Leadership
}
