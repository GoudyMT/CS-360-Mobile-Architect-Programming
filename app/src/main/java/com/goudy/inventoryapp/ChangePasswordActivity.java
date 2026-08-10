package com.goudy.inventoryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goudy.inventoryapp.data.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Forces a freshly provisioned account to replace its temporary password before it can reach
 * the app. On success it clears the sign-in stack and opens the inventory.
 */
public class ChangePasswordActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private static final int MIN_LENGTH = 6;

    private UserRepository userRepository;
    private long userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        userRepository = new UserRepository(this);
        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1);

        // Back returns to sign-in - the user is not signed in until the password is changed.
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.saveButton).setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        String password = text(R.id.newPasswordInput);
        String confirm = text(R.id.confirmPasswordInput);
        if (password.length() < MIN_LENGTH) {
            Toast.makeText(this, R.string.change_password_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.equals(password, confirm)) {
            Toast.makeText(this, R.string.change_password_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }
        userRepository.changePassword(userId, password, this::onChanged);
    }

    private void onChanged() {
        Toast.makeText(this, R.string.change_password_done, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainInventoryActivity.class);
        intent.putExtra(MainInventoryActivity.EXTRA_USER_ID, userId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String text(int id) {
        TextInputEditText field = findViewById(id);
        return field.getText() == null ? "" : field.getText().toString();
    }
}
