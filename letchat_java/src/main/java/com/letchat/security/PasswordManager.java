package com.letchat.security;

import com.letchat.utils.StringTools;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

@Component
public class PasswordManager {

    private static final Pattern LEGACY_MD5 = Pattern.compile("^[a-fA-F0-9]{32}$");

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isLegacyMd5(storedPassword)) {
            String legacyHash = StringTools.encodeMD5(rawPassword);
            return MessageDigest.isEqual(
                    legacyHash.getBytes(StandardCharsets.US_ASCII),
                    storedPassword.toLowerCase().getBytes(StandardCharsets.US_ASCII)
            );
        }
        try {
            return passwordEncoder.matches(rawPassword, storedPassword);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean needsUpgrade(String storedPassword) {
        return storedPassword != null
                && (isLegacyMd5(storedPassword) || passwordEncoder.upgradeEncoding(storedPassword));
    }

    private boolean isLegacyMd5(String storedPassword) {
        return LEGACY_MD5.matcher(storedPassword).matches();
    }
}
