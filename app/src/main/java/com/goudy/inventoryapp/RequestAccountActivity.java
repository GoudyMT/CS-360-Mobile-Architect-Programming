package com.goudy.inventoryapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

/**
 * Collects an access request and saves it as a pending account. The user picks no username
 * or password; Leadership provisions those on approval.
 */
public class RequestAccountActivity extends AppCompatActivity {

    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_account);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        userRepository = new UserRepository(this);

        // Back arrow returns to the sign-in screen.
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Station picker: a sample list now, database-driven later. This install serves one
        // base, so pre-select it when it is the only option.
        String[] stations = getResources().getStringArray(R.array.approved_stations);
        MaterialAutoCompleteTextView baseInput = findViewById(R.id.baseInput);
        baseInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, stations));
        if (stations.length == 1) {
            baseInput.setText(stations[0], false);
        }

        findViewById(R.id.submitRequestButton).setOnClickListener(v -> submit());
    }

    private void submit() {
        String firstName = text(R.id.firstNameInput);
        String lastName = text(R.id.lastNameInput);
        String rate = text(R.id.rateInput);
        String email = text(R.id.emailInput);
        String station = text(R.id.baseInput);
        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(rate)
                || TextUtils.isEmpty(email) || TextUtils.isEmpty(station)) {
            Toast.makeText(this, R.string.request_account_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        userRepository.register(firstName, lastName, rate, email, station, this::onSubmitted);
    }

    private void onSubmitted() {
        Toast.makeText(this, R.string.request_account_submitted, Toast.LENGTH_LONG).show();
        finish();
    }

    private String text(int id) {
        TextView field = findViewById(id);
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
