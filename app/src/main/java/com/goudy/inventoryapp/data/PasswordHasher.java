package com.goudy.inventoryapp.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Turns a password into a salted SHA-256 hash for storage, and checks a candidate against
 * a stored hash. A random per-account salt is kept with the hash (salt$hash), so two people
 * who pick the same password never store the same value and the raw password is never saved.
 */
public final class PasswordHasher {

    private PasswordHasher() { }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return toHex(salt) + "$" + sha256(password, salt);
    }

    public static boolean matches(String password, String stored) {
        int split = stored.indexOf('$');
        if (split < 0) {
            return false;
        }
        byte[] salt = fromHex(stored.substring(0, split));
        return sha256(password, salt).equals(stored.substring(split + 1));
    }

    private static String sha256(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return toHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
