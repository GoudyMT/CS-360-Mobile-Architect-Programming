package com.goudy.inventoryapp;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.InventoryRepository;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.PartType;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * New part form, reached when a scanned or typed SKU is not in inventory. The SKU is pre-filled;
 * everything a scan cannot know is entered by hand. The limit label follows the type - authorized
 * on-hand for allowance, reorder-at level for consumable. Saving inserts the part, and it appears
 * in the grid right away.
 */
public class NewPartActivity extends AppCompatActivity {

    public static final String EXTRA_SKU = "sku";

    private InventoryRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_part);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new InventoryRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // The SKU comes pre-filled from the scan / manual lookup.
        String sku = getIntent().getStringExtra(EXTRA_SKU);
        if (sku != null) {
            ((TextInputEditText) findViewById(R.id.skuInput)).setText(sku);
        }

        MaterialAutoCompleteTextView systemInput = findViewById(R.id.systemInput);
        systemInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.systems)));

        // The inventory-limit label follows the selected type.
        TextInputLayout limitLayout = findViewById(R.id.limitLayout);
        MaterialButtonToggleGroup typeToggle = findViewById(R.id.typeToggle);
        typeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            limitLayout.setHint(getString(checkedId == R.id.typeAllowance
                    ? R.string.new_part_limit_allowance : R.string.new_part_limit_consumable));
        });

        findViewById(R.id.savePartButton).setOnClickListener(v -> savePart());
    }

    private void savePart() {
        String sku = text(R.id.skuInput);
        String name = text(R.id.nameInput);
        String system = text(R.id.systemInput);
        String location = text(R.id.locationInput);
        String limit = text(R.id.limitInput);
        String quantity = text(R.id.qtyInput);
        if (sku.isEmpty() || name.isEmpty() || system.isEmpty() || location.isEmpty()
                || limit.isEmpty() || quantity.isEmpty()) {
            Toast.makeText(this, R.string.new_part_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }

        // Allowance parts track an authorized on-hand; consumables track a reorder threshold.
        boolean allowance = ((MaterialButtonToggleGroup) findViewById(R.id.typeToggle))
                .getCheckedButtonId() == R.id.typeAllowance;
        int limitValue = parseInt(limit);
        Part part = new Part(0, name, system, sku, location,
                allowance ? PartType.ALLOWANCE : PartType.CONSUMABLE, parseInt(quantity),
                allowance ? limitValue : 0, allowance ? 0 : limitValue, 1);

        repository.insert(part);
        Toast.makeText(this, getString(R.string.new_part_saved_toast, name), Toast.LENGTH_SHORT).show();
        finish();
    }

    private String text(int id) {
        TextView field = findViewById(id);
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
