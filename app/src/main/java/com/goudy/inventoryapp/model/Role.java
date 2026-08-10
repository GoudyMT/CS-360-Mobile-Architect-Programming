package com.goudy.inventoryapp.model;

/** Account role - drives which features and admin functions a signed-in user can reach. */
public enum Role {
    TECHNICIAN,   // check out + view
    SUPPLY,       // + receiving, part CRUD, manage systems (Supply / SME)
    LEADERSHIP    // + account control: approve/remove users, change roles
}
