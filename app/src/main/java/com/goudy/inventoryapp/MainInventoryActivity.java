package com.goudy.inventoryapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import com.goudy.inventoryapp.data.UserRepository;
import com.goudy.inventoryapp.model.Part;
import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.StockStatus;
import com.goudy.inventoryapp.model.User;
import com.goudy.inventoryapp.notify.LowStockWorker;
import com.goudy.inventoryapp.ui.PartAdapter;
import com.goudy.inventoryapp.viewmodel.InventoryViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainInventoryActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_SHOW_LOW_OUT = "show_low_out";

    private long signedInUserId;
    private List<Part> allParts = new ArrayList<>();
    private UserRepository userRepository;
    private InventoryViewModel viewModel;
    private PartAdapter adapter;
    private DrawerLayout drawer;
    private TextView partsCount;
    private TextView sortButton;
    private View tileAll;
    private View tileIn;
    private View tileLow;
    private View tileOut;
    private View hamburgerDot;
    private View requestRowDot;
    private boolean isLeadershipUser;
    private int pendingCount;
    private ValueAnimator hamburgerPulse;
    private ValueAnimator rowPulse;

    private StockStatus activeFilter;               // null = show all
    private boolean lowAndOut;                       // Low & Out combined view (everything below reorder)
    private Comparator<Part> activeSort;

    private final Comparator<Part> byName =
            (a, b) -> a.getName().compareToIgnoreCase(b.getName());
    private final Comparator<Part> bySystem =
            (a, b) -> a.getSystem().compareToIgnoreCase(b.getSystem());
    private final Comparator<Part> byQuantity =
            (a, b) -> Integer.compare(a.getQuantity(), b.getQuantity());
    private final Comparator<Part> byStatus =
            (a, b) -> Integer.compare(severity(a.getStatus()), severity(b.getStatus()));

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** Schedules the low-stock sweep for 0800 local each day; survives app close and reboot. */
    private void scheduleLowStockScan() {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(LowStockWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(millisUntilNextRun(8), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "low_stock_scan", ExistingPeriodicWorkPolicy.UPDATE, work);
    }

    /** Millis from now until the next local hour-of-day, so the daily sweep lands at 0800. */
    private long millisUntilNextRun(int hourOfDay) {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hourOfDay);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    /** Asks for notification permission so the daily low-stock alert can actually show. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_inventory);
        scheduleLowStockScan();
        requestNotificationPermission();

        // DrawerLayout owns the window insets here (content + drawer header via
        // fitsSystemWindows), so this screen needs no manual inset listener.
        drawer = findViewById(R.id.drawerLayout);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // Hamburger <-> arrow: the toggle morphs the toolbar icon in sync with the drawer slide.
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.menu_desc, R.string.nav_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.white));

        setupDrawer();
        hamburgerDot = findViewById(R.id.hamburgerDot);

        signedInUserId = getIntent().getLongExtra(EXTRA_USER_ID, -1);
        userRepository = new UserRepository(this);
        loadSignedInUser();

        RecyclerView partsList = findViewById(R.id.partsList);
        partsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PartAdapter(signedInUserId);
        partsList.setAdapter(adapter);

        partsCount = findViewById(R.id.partsCount);
        sortButton = findViewById(R.id.sortButton);

        wireTiles();
        setupSort();

        // The scan FAB opens the receive/add flow; only roles that can receive ever see it.
        findViewById(R.id.scanFab).setOnClickListener(v ->
                startActivity(new Intent(this, ScannerActivity.class)
                        .putExtra(ScannerActivity.EXTRA_USER_ID, signedInUserId)));

        activeSort = byName;
        setSortLabel(R.string.sort_by_name);
        observeInventory();
        setupSearch();

        // A low-stock notification deep-links straight into the Low & Out view.
        if (getIntent().getBooleanExtra(EXTRA_SHOW_LOW_OUT, false)) {
            setLowAndOut();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_SHOW_LOW_OUT, false)) {
            setLowAndOut();
        }
    }

    /** Wires the navigation drawer: highlights the current screen and routes each row. */
    private void setupDrawer() {
        NavigationView navView = findViewById(R.id.navView);
        navView.setCheckedItem(R.id.nav_inventory);

        // Attach the pending dot to the Access requests row in code, keeping a handle for toggling and pulsing it.
        requestRowDot = getLayoutInflater().inflate(R.layout.nav_action_dot, navView, false);
        navView.getMenu().findItem(R.id.nav_access_requests).setActionView(requestRowDot);

        // The hamburger dot fades out as the drawer slides open; the row dot has taken over inside.
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                if (showPendingBadge()) {
                    hamburgerDot.setAlpha(1f - slideOffset);
                    hamburgerDot.setVisibility(slideOffset < 1f ? View.VISIBLE : View.GONE);
                }
            }
        });

        // Edge-to-edge: push the drawer header's content below the status bar. The
        // NavigationView receives the insets and the top one is applied to its header.
        View header = navView.getHeaderView(0);
        int headerBaseTop = getResources().getDimensionPixelSize(R.dimen.space_md);
        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            header.setPadding(header.getPaddingLeft(), headerBaseTop + statusTop,
                    header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inventory) {
                setFilter(null);
            } else if (id == R.id.nav_receive) {
                startActivity(new Intent(this, ScannerActivity.class)
                        .putExtra(ScannerActivity.EXTRA_USER_ID, signedInUserId));
            } else if (id == R.id.nav_low_out) {
                setLowAndOut();
            } else if (id == R.id.nav_access_requests) {
                startActivity(new Intent(this, AccountAdminActivity.class)
                        .putExtra(AccountAdminActivity.EXTRA_USER_ID, signedInUserId));
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_sign_out) {
                Intent signOut = new Intent(this, LoginActivity.class);
                signOut.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(signOut);
                finish();
            } else {
                // These rows don't have a screen yet; keep Inventory highlighted.
                Toast.makeText(this, R.string.nav_stub_toast, Toast.LENGTH_SHORT).show();
                navView.setCheckedItem(R.id.nav_inventory);
            }
            drawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Back closes an open drawer before leaving the screen.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /** Wires each status tile to filter the list; the counts are refreshed as the data changes. */
    private void wireTiles() {
        tileAll = findViewById(R.id.tileAll);
        tileIn = findViewById(R.id.tileIn);
        tileLow = findViewById(R.id.tileLow);
        tileOut = findViewById(R.id.tileOut);
        tileAll.setOnClickListener(v -> setFilter(null));
        tileIn.setOnClickListener(v -> setFilter(StockStatus.IN_STOCK));
        tileLow.setOnClickListener(v -> setFilter(StockStatus.LOW));
        tileOut.setOnClickListener(v -> setFilter(StockStatus.OUT));
    }

    /** Observes the inventory table; the grid and tile counts refresh whenever it changes. */
    private void observeInventory() {
        viewModel = new ViewModelProvider(this).get(InventoryViewModel.class);
        viewModel.getParts().observe(this, parts -> {
            allParts = parts;
            updateTileCounts();
            applyView();
        });
    }

    /** Live search: each keystroke narrows the grid with a DB-side LIKE query on name or SKU. */
    private void setupSearch() {
        EditText searchField = findViewById(R.id.searchField);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable e) { }
        });
    }

    private void updateTileCounts() {
        int in = 0;
        int low = 0;
        int out = 0;
        for (Part part : allParts) {
            switch (part.getStatus()) {
                case IN_STOCK: in++; break;
                case LOW: low++; break;
                default: out++; break;
            }
        }
        ((TextView) findViewById(R.id.tileAllCount)).setText(String.valueOf(allParts.size()));
        ((TextView) findViewById(R.id.tileInCount)).setText(String.valueOf(in));
        ((TextView) findViewById(R.id.tileLowCount)).setText(String.valueOf(low));
        ((TextView) findViewById(R.id.tileOutCount)).setText(String.valueOf(out));
    }

    private void setupSort() {
        sortButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            menu.getMenuInflater().inflate(R.menu.menu_sort, menu.getMenu());
            menu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.sort_name) {
                    activeSort = byName;
                    setSortLabel(R.string.sort_by_name);
                } else if (id == R.id.sort_system) {
                    activeSort = bySystem;
                    setSortLabel(R.string.sort_by_system);
                } else if (id == R.id.sort_quantity) {
                    activeSort = byQuantity;
                    setSortLabel(R.string.sort_by_quantity);
                } else if (id == R.id.sort_status) {
                    activeSort = byStatus;
                    setSortLabel(R.string.sort_by_status);
                } else {
                    return false;
                }
                applyView();
                return true;
            });
            menu.show();
        });
    }

    private void setFilter(StockStatus filter) {
        activeFilter = filter;
        lowAndOut = false;
        applyView();
    }

    /** The Low & Out view: every part at or below its reorder point, low and out together. */
    private void setLowAndOut() {
        activeFilter = null;
        lowAndOut = true;
        applyView();
    }

    private void setSortLabel(int nameRes) {
        sortButton.setText(getString(R.string.sort_label, getString(nameRes)));
    }

    /** Rebuilds the shown list from the active status filter + sort, then refreshes the UI. */
    private void applyView() {
        List<Part> shown = new ArrayList<>();
        for (Part part : allParts) {
            boolean show = lowAndOut
                    ? part.getStatus() != StockStatus.IN_STOCK
                    : (activeFilter == null || part.getStatus() == activeFilter);
            if (show) {
                shown.add(part);
            }
        }
        Collections.sort(shown, activeSort);
        adapter.submitList(shown);
        partsCount.setText(getString(R.string.parts_count, shown.size()));
        updateTileSelection();
    }

    /** Emphasizes the active filter tile and dims the rest (all bright when showing everything). */
    private void updateTileSelection() {
        float dim = 0.45f;
        boolean showingAll = activeFilter == null && !lowAndOut;
        tileAll.setAlpha(showingAll ? 1f : dim);
        tileIn.setAlpha(showingAll || activeFilter == StockStatus.IN_STOCK ? 1f : dim);
        tileLow.setAlpha(showingAll || lowAndOut || activeFilter == StockStatus.LOW ? 1f : dim);
        tileOut.setAlpha(showingAll || lowAndOut || activeFilter == StockStatus.OUT ? 1f : dim);
    }

    /** Loads the signed-in account and fills the drawer header with it. */
    private void loadSignedInUser() {
        if (signedInUserId < 0) {
            return;
        }
        userRepository.getUser(signedInUserId, this::showUserInHeader);
    }

    private void showUserInHeader(User user) {
        if (user == null) {
            return;
        }
        View header = ((NavigationView) findViewById(R.id.navView)).getHeaderView(0);
        ((TextView) header.findViewById(R.id.navHeaderInitials)).setText(initials(user));
        ((TextView) header.findViewById(R.id.navHeaderName))
                .setText(getString(R.string.nav_header_name_fmt, user.getFirstName(), user.getLastName()));
        ((TextView) header.findViewById(R.id.navHeaderMeta))
                .setText(getString(R.string.nav_header_meta_fmt, user.getRate(), user.getStation()));
        ((TextView) header.findViewById(R.id.navHeaderRole)).setText(roleLabel(user.getRole()));
        applyRoleVisibility(user.getRole());
    }

    /** Shows only what the signed-in role may act on: receiving and the audit trail are Supply and
     *  Leadership; approving accounts is Leadership alone. Everything else is open to all roles. */
    private void applyRoleVisibility(Role role) {
        boolean canReceive = role == Role.SUPPLY || role == Role.LEADERSHIP;
        boolean isLeadership = role == Role.LEADERSHIP;
        isLeadershipUser = isLeadership;

        findViewById(R.id.scanFab).setVisibility(canReceive ? View.VISIBLE : View.GONE);

        Menu menu = ((NavigationView) findViewById(R.id.navView)).getMenu();
        menu.findItem(R.id.nav_receive).setVisible(canReceive);
        menu.findItem(R.id.nav_low_out).setVisible(canReceive);
        menu.findItem(R.id.nav_section_oversight).setVisible(canReceive);
        menu.findItem(R.id.nav_audit).setVisible(canReceive);
        menu.findItem(R.id.nav_access_requests).setVisible(isLeadership);

        // Leadership gets a live pending-request badge, driven by the queue.
        if (isLeadership) {
            userRepository.observePending().observe(this, pending -> {
                pendingCount = pending.size();
                updatePendingBadge();
            });
        }
    }

    /** The pending badge shows only to Leadership while requests are waiting. */
    private boolean showPendingBadge() {
        return isLeadershipUser && pendingCount > 0;
    }

    /** Dot on the hamburger while the drawer is closed; on the Access requests row once it opens. */
    private void updatePendingBadge() {
        boolean show = showPendingBadge();
        hamburgerDot.setAlpha(1f);
        hamburgerDot.setVisibility(show && !drawer.isDrawerOpen(GravityCompat.START) ? View.VISIBLE : View.GONE);
        if (requestRowDot != null) {
            requestRowDot.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            startPulses();
        } else {
            stopPulses();
        }
    }

    private void startPulses() {
        if (hamburgerPulse == null) {
            hamburgerPulse = heartbeat(hamburgerDot);
        }
        if (rowPulse == null && requestRowDot != null) {
            rowPulse = heartbeat(requestRowDot);
        }
    }

    private void stopPulses() {
        hamburgerPulse = stopBeat(hamburgerDot, hamburgerPulse);
        rowPulse = stopBeat(requestRowDot, rowPulse);
    }

    /** A soft, repeating scale pulse so a waiting request keeps catching the eye. */
    private ValueAnimator heartbeat(View dot) {
        ValueAnimator beat = ValueAnimator.ofFloat(1f, 1.3f);
        beat.setDuration(800);
        beat.setRepeatCount(ValueAnimator.INFINITE);
        beat.setRepeatMode(ValueAnimator.REVERSE);
        beat.setInterpolator(new AccelerateDecelerateInterpolator());
        beat.addUpdateListener(a -> {
            float scale = (float) a.getAnimatedValue();
            dot.setScaleX(scale);
            dot.setScaleY(scale);
        });
        beat.start();
        return beat;
    }

    private ValueAnimator stopBeat(View dot, ValueAnimator beat) {
        if (beat != null) {
            beat.cancel();
        }
        if (dot != null) {
            dot.setScaleX(1f);
            dot.setScaleY(1f);
        }
        return null;
    }

    private String initials(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        String a = first.isEmpty() ? "" : first.substring(0, 1);
        String b = last.isEmpty() ? "" : last.substring(0, 1);
        return (a + b).toUpperCase(Locale.US);
    }

    private String roleLabel(Role role) {
        if (role == null) {
            return "";
        }
        switch (role) {
            case SUPPLY: return getString(R.string.nav_role_supply);
            case LEADERSHIP: return getString(R.string.nav_role_leadership);
            default: return getString(R.string.nav_role_technician);
        }
    }

    private int severity(StockStatus status) {
        switch (status) {
            case OUT: return 0;
            case LOW: return 1;
            default: return 2;
        }
    }
}
