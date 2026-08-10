package com.goudy.inventoryapp;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.InventoryDatabase;
import com.goudy.inventoryapp.data.UserRepository;
import com.goudy.inventoryapp.model.Role;
import com.goudy.inventoryapp.model.User;
import com.goudy.inventoryapp.model.UserStatus;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Leadership's account admin - the pending access-request queue. Approving generates the username
 * and temp password and activates the account; denying keeps the row as a record. The queue is
 * live, so a handled request drops off on its own.
 */
public class AccountAdminActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private UserRepository userRepository;
    private LinearLayout pendingList;
    private LinearLayout accountList;
    private TextView emptyState;
    private long signedInUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_admin);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        userRepository = new UserRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        pendingList = findViewById(R.id.pendingList);
        accountList = findViewById(R.id.accountList);
        emptyState = findViewById(R.id.emptyState);

        signedInUserId = getIntent().getLongExtra(EXTRA_USER_ID, -1);
        // Accounts are Leadership's alone - re-check on entry, not just via the hidden nav row.
        userRepository.getUser(signedInUserId, user -> {
            if (user == null || user.getRole() != Role.LEADERSHIP) {
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Both lists are live: approvals, denials, and account changes update them on their own.
            userRepository.observePending().observe(this, this::renderQueue);
            userRepository.observeAccounts().observe(this, this::renderAccounts);
        });
    }

    private void renderQueue(List<User> pending) {
        pendingList.removeAllViews();
        emptyState.setVisibility(pending.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (User request : pending) {
            View row = inflater.inflate(R.layout.item_pending_request, pendingList, false);
            ((TextView) row.findViewById(R.id.reqName)).setText(
                    getString(R.string.nav_header_name_fmt, request.getFirstName(), request.getLastName()));
            ((TextView) row.findViewById(R.id.reqMeta)).setText(
                    getString(R.string.admin_request_meta, request.getRate(), request.getEmail()));
            row.findViewById(R.id.approveButton).setOnClickListener(v -> promptApprove(request));
            row.findViewById(R.id.denyButton).setOnClickListener(v -> deny(request));
            pendingList.addView(row);
        }
    }

    /** Approve: Leadership confirms a role (pre-suggested from the rate), then the account is provisioned. */
    private void promptApprove(User request) {
        String[] roleLabels = {
                getString(R.string.nav_role_technician),
                getString(R.string.nav_role_supply),
                getString(R.string.nav_role_leadership)
        };
        Role[] roles = {Role.TECHNICIAN, Role.SUPPLY, Role.LEADERSHIP};
        int[] choice = {suggestedRole(request.getRate())};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.admin_approve_title, request.getFirstName(), request.getLastName()))
                .setSingleChoiceItems(roleLabels, choice[0], (d, which) -> choice[0] = which)
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.admin_approve, (d, w) -> approve(request, roles[choice[0]]))
                .show();
    }

    private void approve(User request, Role role) {
        userRepository.approve(request, role, username -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_provisioned_title)
                .setMessage(getString(R.string.admin_provisioned_msg,
                        username, InventoryDatabase.TEMP_PASSWORD, request.getEmail()))
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private void deny(User request) {
        userRepository.deny(request, () ->
                Toast.makeText(this, R.string.admin_denied_toast, Toast.LENGTH_SHORT).show());
    }

    /** Light default only - Leadership confirms or changes it: LS -> supply, otherwise technician. */
    private int suggestedRole(String rate) {
        String r = rate == null ? "" : rate.trim().toUpperCase(Locale.US);
        return r.startsWith("LS") ? 1 : 0;   // 1 = Supply, 0 = Technician in the role arrays
    }

    /** The provisioned-accounts list: each row shows name + role/status and opens the manage actions. */
    private void renderAccounts(List<User> accounts) {
        accountList.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (User account : accounts) {
            View row = inflater.inflate(R.layout.item_account_row, accountList, false);
            boolean self = account.getId() == signedInUserId;
            ((TextView) row.findViewById(R.id.acctName)).setText(self
                    ? getString(R.string.admin_account_you, account.getFirstName(), account.getLastName())
                    : getString(R.string.nav_header_name_fmt, account.getFirstName(), account.getLastName()));
            ((TextView) row.findViewById(R.id.acctMeta)).setText(accountMeta(account));
            row.setOnClickListener(v -> manageAccount(account));
            accountList.addView(row);
        }
    }

    /** "Role - Status", with the status colored green when active and red when disabled. */
    private CharSequence accountMeta(User account) {
        boolean active = account.getStatus() == UserStatus.ACTIVE;
        String status = getString(active ? R.string.admin_status_active : R.string.admin_status_disabled);
        String text = roleLabel(account.getRole()) + "  -  " + status;
        SpannableString meta = new SpannableString(text);
        int color = ContextCompat.getColor(this, active ? R.color.stock_in : R.color.stock_out);
        meta.setSpan(new ForegroundColorSpan(color), text.length() - status.length(), text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return meta;
    }

    /** Manage one account: change role, reset the password, or disable/enable it. */
    private void manageAccount(User account) {
        boolean active = account.getStatus() == UserStatus.ACTIVE;
        String[] actions = {
                getString(R.string.admin_change_role),
                getString(R.string.admin_reset_password),
                getString(active ? R.string.admin_disable : R.string.admin_enable)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.admin_manage_title, account.getFirstName(), account.getLastName()))
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        changeRolePrompt(account);
                    } else if (which == 1) {
                        confirmReset(account);
                    } else {
                        toggleEnabled(account, !active);
                    }
                })
                .setNegativeButton(R.string.confirm_cancel, null)
                .show();
    }

    /** Blocked for the signed-in user's own account; the repository also refuses to demote the last active leader. */
    private void changeRolePrompt(User account) {
        if (account.getId() == signedInUserId) {
            Toast.makeText(this, R.string.admin_guard_self, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] roleLabels = {
                getString(R.string.nav_role_technician),
                getString(R.string.nav_role_supply),
                getString(R.string.nav_role_leadership)
        };
        Role[] roles = {Role.TECHNICIAN, Role.SUPPLY, Role.LEADERSHIP};
        int current = account.getRole() == Role.LEADERSHIP ? 2 : account.getRole() == Role.SUPPLY ? 1 : 0;
        int[] choice = {current};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_change_role)
                .setSingleChoiceItems(roleLabels, current, (d, which) -> choice[0] = which)
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.admin_apply, (d, w) -> userRepository.changeRole(account, roles[choice[0]],
                        ok -> Toast.makeText(this,
                                ok ? R.string.admin_role_changed_toast : R.string.admin_guard_last_leader,
                                Toast.LENGTH_SHORT).show()))
                .show();
    }

    /** Reset to the shared temporary password; the account must set a new one at next login. */
    private void confirmReset(User account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_reset_confirm_title)
                .setMessage(getString(R.string.admin_reset_confirm_msg,
                        account.getFirstName(), account.getLastName()))
                .setNegativeButton(R.string.confirm_cancel, null)
                .setPositiveButton(R.string.admin_reset_password, (d, w) -> userRepository.resetPassword(account,
                        () -> new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.admin_reset_done_title)
                                .setMessage(getString(R.string.admin_reset_done_msg,
                                        InventoryDatabase.TEMP_PASSWORD, account.getEmail()))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()))
                .show();
    }

    /** Disable is blocked for the signed-in user's own account; the repository refuses to disable the last active leader. */
    private void toggleEnabled(User account, boolean enable) {
        if (!enable && account.getId() == signedInUserId) {
            Toast.makeText(this, R.string.admin_guard_self, Toast.LENGTH_SHORT).show();
            return;
        }
        userRepository.setEnabled(account, enable, ok -> Toast.makeText(this,
                ok ? (enable ? R.string.admin_enabled_toast : R.string.admin_disabled_toast)
                        : R.string.admin_guard_last_leader,
                Toast.LENGTH_SHORT).show());
    }

    private String roleLabel(Role role) {
        if (role == Role.SUPPLY) {
            return getString(R.string.nav_role_supply);
        }
        if (role == Role.LEADERSHIP) {
            return getString(R.string.nav_role_leadership);
        }
        return getString(R.string.nav_role_technician);
    }
}
