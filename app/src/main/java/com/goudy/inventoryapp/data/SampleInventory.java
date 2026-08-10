package com.goudy.inventoryapp.data;

import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.PartType;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed inventory for the database - the rows the app starts with on first run.
 */
public final class SampleInventory {

    private SampleInventory() { }

    public static List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        // id, name, system, partNumber, location, type, quantity, authorizedQty, lowThreshold, versionNumber
        parts.add(new Part(1, "Magnetron", "PAR", "M2314-A", "Bay A-12", PartType.ALLOWANCE, 1, 1, 0, 1));
        parts.add(new Part(2, "Receiver Module", "DASR", "RX-4471", "Bay B-03", PartType.ALLOWANCE, 1, 2, 0, 1));
        parts.add(new Part(3, "Power Amplifier", "TACAN", "PA-9007", "Bay C-07", PartType.ALLOWANCE, 0, 1, 0, 1));
        parts.add(new Part(4, "Waveguide Gasket", "PAR", "WG-1580", "Bay A-04", PartType.CONSUMABLE, 42, 0, 10, 1));
        parts.add(new Part(5, "Antenna Drive Motor", "DASR", "ADM-330", "Bay B-06", PartType.CONSUMABLE, 3, 0, 4, 1));
        parts.add(new Part(6, "Coax Connector N-Type", "TACAN", "CX-221", "Bay C-01", PartType.CONSUMABLE, 60, 0, 15, 1));
        return parts;
    }
}
