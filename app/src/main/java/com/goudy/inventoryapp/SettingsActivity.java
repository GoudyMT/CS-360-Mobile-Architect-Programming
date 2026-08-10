package com.goudy.inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Permissions hub - one place in the nav to see and change what the app can do.
 * Tapping a permission that is off asks for it right here (the real grant dialog);
 * once it is on, or if it was permanently denied, it hands off to system settings,
 * the only place those can be changed. The SMS row opens its own rationale screen.
 */
public class SettingsActivity extends AppCompatActivity {

    private TextView cameraState;
    private TextView smsState;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                refreshStates();
                // If the OS refused to even show the dialog (permanently denied), settings is the only path.
                if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.CAMERA)) {
                    openAppSettings();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cameraState = findViewById(R.id.cameraState);
        smsState = findViewById(R.id.smsState);

        // Camera off -> ask in-app; camera on -> system settings (the only place to revoke).
        findViewById(R.id.cameraRow).setOnClickListener(v -> {
            if (isGranted(Manifest.permission.CAMERA)) {
                openAppSettings();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });
        // SMS has its own rationale + request screen.
        findViewById(R.id.smsRow).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStates();
    }

    private void refreshStates() {
        setState(cameraState, isGranted(Manifest.permission.CAMERA));
        setState(smsState, isGranted(Manifest.permission.SEND_SMS));
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void setState(TextView view, boolean on) {
        view.setText(on ? R.string.settings_on : R.string.settings_off);
        view.setTextColor(ContextCompat.getColor(this, on ? R.color.stock_in : R.color.ink_muted));
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }
}
