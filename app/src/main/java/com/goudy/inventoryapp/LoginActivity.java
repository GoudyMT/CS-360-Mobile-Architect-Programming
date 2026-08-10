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

import com.goudy.inventoryapp.data.LoginResult;
import com.goudy.inventoryapp.data.UserRepository;
import com.goudy.inventoryapp.model.User;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        userRepository = new UserRepository(this);

        // "Request account" opens the access-request screen.
        findViewById(R.id.requestAccountButton).setOnClickListener(v ->
                startActivity(new Intent(this, RequestAccountActivity.class)));

        findViewById(R.id.loginButton).setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = text(R.id.usernameInput).trim();
        String password = text(R.id.passwordInput);
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.login_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        userRepository.login(username, password, this::onLoginResult);
    }

    private void onLoginResult(LoginResult result) {
        switch (result.getOutcome()) {
            case SUCCESS:
                onSignedIn(result.getUser());
                break;
            case DISABLED:
                Toast.makeText(this, R.string.login_disabled, Toast.LENGTH_LONG).show();
                break;
            default:
                Toast.makeText(this, R.string.login_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void onSignedIn(User user) {
        // A freshly provisioned account must replace its temporary password before reaching the app.
        if (user.isMustChangePassword()) {
            Intent intent = new Intent(this, ChangePasswordActivity.class);
            intent.putExtra(ChangePasswordActivity.EXTRA_USER_ID, user.getId());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, MainInventoryActivity.class);
            intent.putExtra(MainInventoryActivity.EXTRA_USER_ID, user.getId());
            startActivity(intent);
            finish();
        }
    }

    private String text(int id) {
        TextInputEditText field = findViewById(id);
        return field.getText() == null ? "" : field.getText().toString();
    }
}
