package com.letchat.security;

import com.letchat.utils.StringTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordManagerTest {

    private final PasswordManager passwordManager = new PasswordManager();

    @Test
    void encodesNewPasswordsWithBcrypt() {
        String encoded = passwordManager.encode("Test@123456");

        assertTrue(encoded.startsWith("$2"));
        assertTrue(passwordManager.matches("Test@123456", encoded));
        assertFalse(passwordManager.matches("wrong-password", encoded));
    }

    @Test
    void acceptsLegacyMd5AndMarksItForUpgrade() {
        String legacyHash = StringTools.encodeMD5("Test@123456");

        assertTrue(passwordManager.matches("Test@123456", legacyHash));
        assertTrue(passwordManager.needsUpgrade(legacyHash));
        assertFalse(passwordManager.matches("wrong-password", legacyHash));
    }
}
