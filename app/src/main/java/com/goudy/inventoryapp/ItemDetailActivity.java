package com.goudy.inventoryapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.InventoryRepository;
import com.goudy.inventoryapp.data.UserRepository;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.PartType;
import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.StockStatus;
import com.goudy.inventoryapp.notify.Notifier;
import com.goudy.inventoryapp.notify.SmsNotifier;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

/**
 * Full detail for one part, loaded from the database by id. Check out morphs the bottom of the
 * screen in place - the action buttons slide down and a live scan camera fills their spot while
 * the part data stays fixed above - so the user verifies against the part without leaving the page.
 * Confirming takes a job number and commits the checkout atomically.
 */
public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PART_ID = "part_id";
    public static final String EXTRA_USER_ID = "user_id";

    private InventoryRepository repository;
    private Notifier notifier;
    private Part part;
    private long userId;
    private View actionStack;
    private View cameraPanel;
    private BarcodeView checkoutCamera;
    private boolean scanning;
    private String lastRejected;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    enterScan();
                } else {
                    Toast.makeText(this, R.string.detail_camera_needed, Toast.LENGTH_LONG).show();
                }
            });

    private final BarcodeCallback checkoutDecodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            handleScan(result.getText());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_item_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new InventoryRepository(this);
        notifier = new SmsNotifier(this);
        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1);

        // Delete removes a part - a Supply/Leadership action, so reveal it only for those roles.
        new UserRepository(this).getUser(userId, user -> {
            if (user != null && (user.getRole() == Role.SUPPLY || user.getRole() == Role.LEADERSHIP)) {
                findViewById(R.id.deleteGroup).setVisibility(View.VISIBLE);
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> goUp());

        actionStack = findViewById(R.id.actionStack);
        cameraPanel = findViewById(R.id.cameraPanel);
        checkoutCamera = findViewById(R.id.checkoutCamera);
        checkoutCamera.decodeContinuous(checkoutDecodeCallback);

        findViewById(R.id.checkOutButton).setOnClickListener(v -> attemptCheckout());
        findViewById(R.id.reportButton).setOnClickListener(v -> showReportSheet());
        findViewById(R.id.deleteButton).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.cancelButton).setOnClickListener(v -> exitScan());
        findViewById(R.id.confirmButton).setOnClickListener(v -> promptManualVerify());

        // Back closes the scan camera first, then leaves the screen.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goUp();
            }
        });

        // Load the part from the database, then fill the screen.
        repository.getPart(getIntent().getLongExtra(EXTRA_PART_ID, -1), this::onPartLoaded);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanning) {
            checkoutCamera.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        checkoutCamera.pause();
    }

    private void onPartLoaded(Part loaded) {
        if (loaded == null) {
            finish();
            return;
        }
        part = loaded;
        bindPart(loaded);
    }

    private void bindPart(Part part) {
        StockStatus status = part.getStatus();
        int textColor = ContextCompat.getColor(this, textColorRes(status));

        findViewById(R.id.heroAccent).setBackgroundColor(ContextCompat.getColor(this, barColorRes(status)));
        ((TextView) findViewById(R.id.detailName)).setText(part.getName());
        ((TextView) findViewById(R.id.detailSystemTag)).setText(part.getSystem());

        TextView quantity = findViewById(R.id.detailQuantity);
        quantity.setText(String.valueOf(part.getQuantity()));
        quantity.setTextColor(textColor);

        TextView statusLabel = findViewById(R.id.detailStatus);
        statusLabel.setText(labelRes(status));
        statusLabel.setTextColor(textColor);

        // Tolerance context is per type: allowance shows authorized on-hand, consumable its reorder level.
        TextView context = findViewById(R.id.detailContext);
        if (part.getType() == PartType.ALLOWANCE) {
            context.setText(getString(R.string.detail_allowance_ctx, part.getQuantity(), part.getAuthorizedQty()));
        } else {
            context.setText(getString(R.string.detail_consumable_ctx, part.getLowThreshold()));
        }

        ((TextView) findViewById(R.id.detailPartNumber)).setText(part.getPartNumber());
        ((TextView) findViewById(R.id.detailLocation)).setText(part.getLocation());
        ((TextView) findViewById(R.id.detailType)).setText(
                part.getType() == PartType.ALLOWANCE ? R.string.type_allowance : R.string.type_consumable);
        ((TextView) findViewById(R.id.detailSystem)).setText(systemName(part.getSystem()));

        // Cannot check out what is not on hand - the report path is how an out-of-stock part gets flagged.
        findViewById(R.id.checkOutButton).setEnabled(part.getQuantity() > 0);
    }

    /** Check out needs the camera to confirm the part; request it first if it isn't granted. */
    private void attemptCheckout() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            enterScan();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void enterScan() {
        scanning = true;
        lastRejected = null;
        morph();
        actionStack.setVisibility(View.GONE);
        cameraPanel.setVisibility(View.VISIBLE);
        checkoutCamera.resume();
    }

    private void exitScan() {
        scanning = false;
        morph();
        cameraPanel.setVisibility(View.GONE);
        actionStack.setVisibility(View.VISIBLE);
        checkoutCamera.pause();
    }

    /**
     * A scanned (or typed) SKU verifies against the part on screen: a match advances to the job
     * number; a wrong part buzzes and keeps scanning, so the wrong item can't be checked out.
     */
    private void handleScan(String scanned) {
        if (part == null || scanned == null || scanned.trim().isEmpty()) {
            return;
        }
        String value = scanned.trim();
        if (value.equalsIgnoreCase(part.getPartNumber().trim())) {
            lastRejected = null;
            checkoutCamera.pause();
            Toast.makeText(this, R.string.checkout_verified, Toast.LENGTH_SHORT).show();
            promptJobNumber();
        } else if (!value.equalsIgnoreCase(lastRejected)) {
            // New wrong part in view: buzz once and keep scanning (don't re-buzz the same one).
            lastRejected = value;
            vibrate();
            Toast.makeText(this, getString(R.string.checkout_wrong_part, part.getPartNumber(), value),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Fallback when the camera can't read the code: type the part number to confirm the same way. */
    private void promptManualVerify() {
        if (part == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint(R.string.checkout_manual_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_margin);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, 0, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.checkout_manual_title)
                .setView(container)
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.confirm_confirm, (dialog, which) ->
                        handleScan(input.getText().toString()))
                .show();
    }

    /** Re-arms scanning after the job-number prompt is dismissed without checking out. */
    private void resumeScan() {
        if (scanning) {
            lastRejected = null;
            checkoutCamera.resume();
        }
    }

    private void vibrate() {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    /** One fluid motion: buttons slide down and out, the camera fades in over their spot. */
    private void morph() {
        TransitionSet set = new TransitionSet();
        set.addTransition(new ChangeBounds());
        set.addTransition(new Slide(Gravity.BOTTOM).addTarget(actionStack));
        set.addTransition(new Fade().addTarget(cameraPanel));
        set.setOrdering(TransitionSet.ORDERING_TOGETHER);
        TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.main), set);
    }

    /** Scan confirmed: capture the job number, then commit the checkout. */
    private void promptJobNumber() {
        if (part == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint(R.string.checkout_job_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_margin);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, 0, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.checkout_job_title)
                .setView(container)
                .setNegativeButton(R.string.confirm_cancel, (dialog, which) -> resumeScan())
                .setPositiveButton(R.string.detail_check_out, (dialog, which) ->
                        checkout(input.getText().toString().trim()))
                .setOnCancelListener(dialog -> resumeScan())
                .show();
    }

    private void checkout(String jobNumber) {
        Part checkedOut = part;
        repository.checkout(checkedOut, userId, jobNumber, updated -> {
            if (updated != null) {
                Toast.makeText(this, R.string.confirm_checkout_toast, Toast.LENGTH_SHORT).show();
                maybeAlertLowStock(updated);
                finish();
            } else {
                // Someone changed the row first (or it hit zero) - reload the fresh part and let them retry.
                Toast.makeText(this, R.string.checkout_stock_changed, Toast.LENGTH_LONG).show();
                exitScan();
                repository.getPart(checkedOut.getId(), this::onPartLoaded);
            }
        });
    }

    /** A checkout that leaves a part at or below its reorder level texts supply (when SMS is on). */
    private void maybeAlertLowStock(Part updated) {
        if (updated.getStatus() != StockStatus.IN_STOCK) {
            notifier.lowStock(updated);
        }
    }

    private void confirmDelete() {
        if (part == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_delete_confirm_title)
                .setMessage(getString(R.string.detail_delete_confirm_msg, part.getName()))
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.detail_delete, (dialog, which) -> {
                    repository.delete(part);
                    Toast.makeText(this, R.string.detail_deleted_toast, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    /** Discrepancy report: pre-filled reason options for the item. */
    // A bottom sheet has no parent view at inflation, so the null root is correct here.
    @SuppressLint("InflateParams")
    private void showReportSheet() {
        if (part == null) {
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_report_discrepancy, null);
        ((TextView) content.findViewById(R.id.reportPart))
                .setText(getString(R.string.report_part, part.getName(), part.getPartNumber()));
        content.findViewById(R.id.reportSend).setOnClickListener(v -> {
            sheet.dismiss();
            Toast.makeText(this, R.string.report_sent_toast, Toast.LENGTH_SHORT).show();
        });
        sheet.setContentView(content);
        sheet.show();
    }

    private void goUp() {
        if (scanning) {
            exitScan();
        } else {
            finish();
        }
    }

    private CharSequence systemName(String code) {
        switch (code) {
            case "PAR": return getString(R.string.system_par);
            case "DASR": return getString(R.string.system_dasr);
            case "TACAN": return getString(R.string.system_tacan);
            default: return code;
        }
    }

    private int barColorRes(StockStatus status) {
        switch (status) {
            case IN_STOCK: return R.color.stock_in;
            case LOW: return R.color.stock_low;
            default: return R.color.stock_out;
        }
    }

    private int textColorRes(StockStatus status) {
        switch (status) {
            case IN_STOCK: return R.color.stock_in_on;
            case LOW: return R.color.stock_low_on;
            default: return R.color.stock_out_on;
        }
    }

    private int labelRes(StockStatus status) {
        switch (status) {
            case IN_STOCK: return R.string.status_in_stock;
            case LOW: return R.string.status_low;
            default: return R.string.status_out;
        }
    }
}
