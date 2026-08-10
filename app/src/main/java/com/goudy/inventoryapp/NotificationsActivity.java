package com.goudy.inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.goudy.inventoryapp.notify.LowStockWorker;
import com.goudy.inventoryapp.notify.NotificationPrefs;
import com.goudy.inventoryapp.notify.SmsNotifier;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Notifications & alerts - the SMS permission home. It checks the SEND_SMS permission and shows
 * one of three states: off, with the rationale and a request; on, with the alert toggles, a test
 * send, and an on-demand stock check; or denied, where the app still works and links out to
 * settings. Sending and the background sweep run through the Notifier.
 */
public class NotificationsActivity extends AppCompatActivity {

    private View offGroup;
    private View onGroup;
    private View deniedGroup;

    private final ActivityResultLauncher<String> smsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> refreshState());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        offGroup = findViewById(R.id.smsOffGroup);
        onGroup = findViewById(R.id.smsOnGroup);
        deniedGroup = findViewById(R.id.smsDeniedGroup);

        findViewById(R.id.enableSmsButton).setOnClickListener(v -> requestSms());
        findViewById(R.id.testAlertButton).setOnClickListener(v -> new SmsNotifier(this).sendTest());
        findViewById(R.id.checkStockButton).setOnClickListener(v -> runLowStockCheck());
        findViewById(R.id.openSettingsButton).setOnClickListener(v -> openAppSettings());
        wireAlertToggles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    /** Shows the state that matches the current SEND_SMS permission. */
    private void refreshState() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        boolean permanentlyDenied = !granted && smsAsked()
                && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS);

        onGroup.setVisibility(granted ? View.VISIBLE : View.GONE);
        deniedGroup.setVisibility(permanentlyDenied ? View.VISIBLE : View.GONE);
        offGroup.setVisibility(!granted && !permanentlyDenied ? View.VISIBLE : View.GONE);
    }

    /** Restores the alert toggles from storage and saves any change, so they survive leaving the screen. */
    private void wireAlertToggles() {
        CompoundButton lowStock = findViewById(R.id.lowStockSwitch);
        lowStock.setChecked(NotificationPrefs.lowStockAlertsOn(this));
        lowStock.setOnCheckedChangeListener((v, on) -> NotificationPrefs.setLowStockAlerts(this, on));

        CompoundButton discrepancy = findViewById(R.id.discrepancySwitch);
        discrepancy.setChecked(NotificationPrefs.discrepancyReportsOn(this));
        discrepancy.setOnCheckedChangeListener((v, on) -> NotificationPrefs.setDiscrepancyReports(this, on));
    }

    private void requestSms() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("sms_asked", true).apply();
        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
    }

    private boolean smsAsked() {
        return getPreferences(MODE_PRIVATE).getBoolean("sms_asked", false);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    /** Kicks off the background sweep on demand so a low-stock text can be seen without the wait. */
    private void runLowStockCheck() {
        WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(LowStockWorker.class).build());
        Toast.makeText(this, R.string.notif_check_running, Toast.LENGTH_SHORT).show();
    }
}
