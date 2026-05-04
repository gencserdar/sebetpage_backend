package com.serdar.auth.service;

import com.serdar.common.ServiceException;

/**
 * Single source of truth for what counts as an acceptable password. Called
 * from register / requestPasswordChange / resetPassword so the rules can't
 * silently diverge between flows.
 *
 * Rules (deliberately mild — strong enough to neuter the worst dictionary
 * attacks without making the form annoying):
 *   - 8 characters minimum
 *   - at least one letter (any case)
 *   - at least one digit
 *
 * Tighten per project taste. If you ever want to plug in a real "have I
 * been pwned" check or zxcvbn-style entropy scoring, this is the seam.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ServiceException.invalid("Password must be at least " + MIN_LENGTH + " characters");
        }
        boolean hasLetter = false, hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch))      hasLetter = true;
            else if (Character.isDigit(ch))  hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw ServiceException.invalid("Password must contain at least one letter and one digit");
        }
    }
}
