package com.goudy.inventoryapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.InventoryRepository;
import com.goudy.inventoryapp.data.LookupResult;
import com.goudy.inventoryapp.data.UserRepository;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.PartType;
import com.goudy.inventoryapp.model.Role;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

import java.util.List;

/**
 * Receive / Add flow. A scan yields only a SKU, so the flow forks on the lookup:
 * a known part goes to confirm-and-receive (set quantity, add to on-hand); an
 * unknown SKU offers the New Part form where the rest is entered by hand. The
 * camera decodes a barcode straight into that lookup; manual entry feeds the same
 * fork whenever the camera can't be used.
 */
public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private View scanGroup;
    private View receiveGroup;
    private View cameraOffGroup;
    private TextView cameraOffText;
    private MaterialButton enableCameraButton;
    private TextView qtyText;
    private Part loadedPart;
    private int quantity = 1;
    private boolean torchOn;
    private InventoryRepository repository;
    private BarcodeView barcodeView;
    private boolean handlingResult;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        refreshCameraState();
                        resumeCameraIfReady();
                    });

    private final BarcodeCallback decodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (handlingResult) {
                return;
            }
            String sku = result.getText();
            if (sku == null || sku.trim().isEmpty()) {
                return;
            }
            // Stop scanning on the first hit; the receive / near-match / new-part fork takes over.
            handlingResult = true;
            barcodeView.pause();
            lookUp(sku.trim());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new InventoryRepository(this);

        // Receiving is a Supply/Leadership action - re-check the signed-in role here, not just the hidden button.
        new UserRepository(this).getUser(getIntent().getLongExtra(EXTRA_USER_ID, -1), user -> {
            if (user == null || (user.getRole() != Role.SUPPLY && user.getRole() != Role.LEADERSHIP)) {
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> goBack());

        scanGroup = findViewById(R.id.scanGroup);
        receiveGroup = findViewById(R.id.receiveGroup);
        cameraOffGroup = findViewById(R.id.cameraOffGroup);
        cameraOffText = findViewById(R.id.cameraOffText);
        enableCameraButton = findViewById(R.id.enableCameraButton);
        qtyText = findViewById(R.id.qtyText);
        barcodeView = findViewById(R.id.barcodeView);
        barcodeView.decodeContinuous(decodeCallback);

        enableCameraButton.setOnClickListener(v -> onEnableCamera());
        findViewById(R.id.torchButton).setOnClickListener(v -> toggleTorch());
        findViewById(R.id.enterManualButton).setOnClickListener(v -> promptManualEntry());
        findViewById(R.id.decreaseButton).setOnClickListener(v -> changeQuantity(-1));
        findViewById(R.id.increaseButton).setOnClickListener(v -> changeQuantity(1));
        findViewById(R.id.notThisButton).setOnClickListener(v -> showScan());
        findViewById(R.id.addInventoryButton).setOnClickListener(v -> addToInventory());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCameraState();
        resumeCameraIfReady();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    /** Covers the viewfinder when CAMERA is missing; manual entry always stays available below. */
    private void refreshCameraState() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            cameraOffGroup.setVisibility(View.GONE);
            return;
        }
        cameraOffGroup.setVisibility(View.VISIBLE);
        if (cameraPermanentlyDenied()) {
            cameraOffText.setText(R.string.scanner_cam_denied);
            enableCameraButton.setText(R.string.notif_open_settings);
            enableCameraButton.setIconResource(R.drawable.ic_settings);
        } else {
            cameraOffText.setText(R.string.scanner_cam_needed);
            enableCameraButton.setText(R.string.scanner_cam_enable);
            enableCameraButton.setIconResource(R.drawable.ic_camera);
        }
    }

    private void onEnableCamera() {
        if (cameraPermanentlyDenied()) {
            openAppSettings();
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("camera_asked", true).apply();
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean cameraPermanentlyDenied() {
        return getPreferences(MODE_PRIVATE).getBoolean("camera_asked", false)
                && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    /** Runs the live preview and arms one decode, only while CAMERA is granted and the scan screen is showing. */
    private void resumeCameraIfReady() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (granted && scanGroup.getVisibility() == View.VISIBLE) {
            handlingResult = false;
            barcodeView.resume();
        } else {
            barcodeView.pause();
        }
    }

    private void promptManualEntry() {
        EditText input = new EditText(this);
        input.setHint(R.string.scanner_manual_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_margin);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, 0, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scanner_manual_title)
                .setView(container)
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.scanner_lookup, (dialog, which) ->
                        lookUp(input.getText().toString().trim()))
                .show();
    }

    /**
     * Every add-new flows through this lookup so duplicates can't sneak in: an exact
     * SKU receives; near matches are surfaced first (typo guard); only a true miss
     * offers the New Part form, always with the looked-up number pre-filled.
     */
    private void lookUp(String partNumber) {
        if (partNumber.isEmpty()) {
            return;
        }
        repository.lookUp(partNumber, result -> {
            switch (result.getOutcome()) {
                case FOUND:
                    showReceive(result.getMatch());
                    break;
                case NEAR:
                    showNearMatches(partNumber, result.getNear());
                    break;
                default:
                    promptAddNew(partNumber);
            }
        });
    }

    /** Typo guard: surface similar parts as clearly tappable options before adding a new one. */
    // A bottom sheet has no parent view at inflation, so the null root is correct here.
    @SuppressLint("InflateParams")
    private void showNearMatches(String query, List<Part> near) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_near_matches, null);
        ((TextView) content.findViewById(R.id.nearSubtitle))
                .setText(getString(R.string.scanner_near_subtitle, query));

        LinearLayout list = content.findViewById(R.id.nearList);
        for (Part part : near) {
            View row = getLayoutInflater().inflate(R.layout.item_near_match, list, false);
            ((TextView) row.findViewById(R.id.matchName)).setText(part.getName());
            ((TextView) row.findViewById(R.id.matchMeta))
                    .setText(getString(R.string.scanner_near_meta, part.getPartNumber(), part.getLocation()));
            row.setOnClickListener(v -> {
                sheet.dismiss();
                showReceive(part);
            });
            list.addView(row);
        }

        content.findViewById(R.id.nearAddNew).setOnClickListener(v -> {
            sheet.dismiss();
            openNewPart(query);
        });
        sheet.setContentView(content);
        sheet.setOnDismissListener(d -> resumeCameraIfReady());
        sheet.show();
    }

    private void promptAddNew(String partNumber) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scanner_not_found_title)
                .setMessage(getString(R.string.scanner_not_found_msg, partNumber))
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.scanner_add_new_part, (dialog, which) -> openNewPart(partNumber))
                .setOnDismissListener(dialog -> resumeCameraIfReady())
                .show();
    }

    private void openNewPart(String sku) {
        Intent intent = new Intent(this, NewPartActivity.class);
        if (sku != null) {
            intent.putExtra(NewPartActivity.EXTRA_SKU, sku);
        }
        startActivity(intent);
    }

    private void showReceive(Part part) {
        loadedPart = part;
        quantity = 1;
        qtyText.setText(String.valueOf(quantity));
        ((TextView) findViewById(R.id.receiveName)).setText(part.getName());
        ((TextView) findViewById(R.id.receiveSystemTag)).setText(part.getSystem());
        String typeLabel = getString(part.getType() == PartType.ALLOWANCE
                ? R.string.type_allowance : R.string.type_consumable);
        ((TextView) findViewById(R.id.receiveMeta)).setText(
                getString(R.string.scanner_meta, part.getPartNumber(), part.getLocation(), typeLabel));
        scanGroup.setVisibility(View.GONE);
        receiveGroup.setVisibility(View.VISIBLE);
        barcodeView.pause();
    }

    private void showScan() {
        loadedPart = null;
        receiveGroup.setVisibility(View.GONE);
        scanGroup.setVisibility(View.VISIBLE);
        resumeCameraIfReady();
    }

    private void changeQuantity(int delta) {
        quantity = Math.max(1, quantity + delta);
        qtyText.setText(String.valueOf(quantity));
    }

    private void addToInventory() {
        Part part = loadedPart;
        int amount = quantity;
        repository.receive(part, amount, () -> {
            Toast.makeText(this, getString(R.string.scanner_received_toast, amount, part.getName()),
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** Toggles the camera torch and mirrors the on/off state on the icon. */
    private void toggleTorch() {
        torchOn = !torchOn;
        barcodeView.setTorch(torchOn);
        ImageView torch = findViewById(R.id.torchButton);
        torch.setColorFilter(ContextCompat.getColor(this, torchOn ? R.color.amber : R.color.white_70));
    }

    private void goBack() {
        if (receiveGroup.getVisibility() == View.VISIBLE) {
            showScan();
        } else {
            finish();
        }
    }
}
