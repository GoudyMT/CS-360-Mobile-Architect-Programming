package com.goudy.inventoryapp.data;

import com.goudy.inventoryapp.model.Part;

import java.util.List;

/**
 * The result of a scan lookup: an exact match (FOUND), similar SKUs to disambiguate (NEAR - the
 * typo guard), or a true miss (NONE) that leads to the New Part form.
 */
public class LookupResult {

    public enum Outcome { FOUND, NEAR, NONE }

    private final Outcome outcome;
    private final Part match;
    private final List<Part> near;

    private LookupResult(Outcome outcome, Part match, List<Part> near) {
        this.outcome = outcome;
        this.match = match;
        this.near = near;
    }

    static LookupResult found(Part match) {
        return new LookupResult(Outcome.FOUND, match, null);
    }

    static LookupResult near(List<Part> near) {
        return new LookupResult(Outcome.NEAR, null, near);
    }

    static LookupResult none() {
        return new LookupResult(Outcome.NONE, null, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Part getMatch() {
        return match;
    }

    public List<Part> getNear() {
        return near;
    }
}
