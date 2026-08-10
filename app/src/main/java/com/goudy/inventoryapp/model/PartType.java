package com.goudy.inventoryapp.model;

/**
 * How a part's stock status is judged. Allowance parts have a fixed authorized
 * on-hand quantity; consumables are reordered against a low threshold.
 */
public enum PartType {
    ALLOWANCE,
    CONSUMABLE
}
