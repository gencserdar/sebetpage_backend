package com.serdar.auth.service;

import com.serdar.auth.entity.Credential;
import com.serdar.auth.entity.Role;
import com.serdar.auth.repository.CredentialRepository;
import com.serdar.common.ServiceException;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Business logic for auth flows — register, login, activate, refresh, etc.
 * The gRPC layer ({@link com.serdar.auth.grpc.AuthGrpcService}) is a thin
 * adapter over this class, so it can also be reused from tests without spinning
 * up a gRPC server.
 */
@Service
@RequiredArgsConstructor
public class AuthDomainService {

    private final CredentialRepository repo;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwt;
    private final EmailService email;
    private final RefreshTokenHasher refreshHasher;

    @Value("${refresh.expiration.default}")  int refreshDefault;
    @Value("${refresh.expiration.remember}") int refreshRemember;
    @Value("${frontend.base-url}") String frontendBaseUrl;
    @Value("${gateway.base-url}")  String gatewayBaseUrl;
    @Value("${app.reset-code-ttl-minutes}") int resetCodeTtlMinutes;
    @Value("${app.reset-code-max-attempts}") int resetCodeMaxAttempts;
    @Value("${app.account-change-code-ttl-minutes}") int accountChangeCodeTtlMinutes;
    @Value("${app.account-change-code-max-attempts}") int accountChangeCodeMaxAttempts;

    public record Registered(Credential credential, String activationCode) {}

    @PostConstruct
    void validateSecurityConfig() {
        if (resetCodeTtlMinutes <= 0) throw new IllegalStateException("app.reset-code-ttl-minutes must be positive");
        if (resetCodeMaxAttempts <= 0) throw new IllegalStateException("app.reset-code-max-attempts must be positive");
        if (accountChangeCodeTtlMinutes <= 0) throw new IllegalStateException("app.account-change-code-ttl-minutes must be positive");
        if (accountChangeCodeMaxAttempts <= 0) throw new IllegalStateException("app.account-change-code-max-attempts must be positive");
    }

    // RFC-5322-lite: local@domain.tld — rejects obvious garbage without being
    // overly strict. Real delivery failure is caught by the SMTP bounce path.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private static void validateEmailFormat(String emailAddr) {
        if (emailAddr == null || emailAddr.isBlank() || emailAddr.length() > 254
                || !EMAIL_PATTERN.matcher(emailAddr).matches()) {
            throw ServiceException.invalid("Invalid email address");
        }
    }

    @Transactional
    public Registered register(String emailAddr, String rawPassword, String nickname) {
        validateEmailFormat(emailAddr);
        if (repo.existsByEmail(emailAddr))      throw ServiceException.conflict("Email already used");
        if (repo.existsByNickname(nickname))    throw ServiceException.conflict("Nickname already used");
        PasswordPolicy.validate(rawPassword);

        String activationCode = UUID.randomUUID().toString();

        Credential c = Credential.builder()
                .email(emailAddr)
                .nickname(nickname)
                .password(encoder.encode(rawPassword))
                .role(Role.USER)
                .activated(false)
                .activationCode(activationCode)
                .build();
        repo.save(c);

        String link = gatewayBaseUrl + "/api/auth/activate?code=" + activationCode;
        email.send(emailAddr, "Activate your account", "Please click this link to activate: " + link);
        return new Registered(c, activationCode);
    }

    public record LoginResult(Credential credential, String accessToken, String refreshToken, int cookieAge) {}

    @Transactional
    public LoginResult login(String emailAddr, String rawPassword, boolean rememberMe) {
        Credential c = repo.findByEmail(emailAddr)
                .orElseThrow(() -> ServiceException.unauth("Invalid credentials"));
        if (!encoder.matches(rawPassword, c.getPassword()))
            throw ServiceException.unauth("Invalid credentials");
        if (!Boolean.TRUE.equals(c.getActivated()))
            throw ServiceException.precondition("Account is not activated. Check your email.");

        int cookieAge = rememberMe ? refreshRemember : refreshDefault;
        String access = jwt.issueAccess(c);
        String refresh = jwt.issueRefresh(c, cookieAge);
        // Refresh-token-at-rest is HMAC-SHA-256, not bcrypt. The token is a
        // 256-bit random JWT — bcrypt's anti-brute-force slowness adds zero
        // value here and burns ~200ms of CPU per login. See RefreshTokenHasher
        // for the rationale.
        c.setRefreshToken(refreshHasher.hash(refresh));
        c.setRememberMe(rememberMe);
        repo.save(c);
        // The plaintext refresh JWT goes back to the client (HttpOnly cookie);
        // the DB only ever sees the HMAC hash.
        return new LoginResult(c, access, refresh, cookieAge);
    }

    /**
     * Rotate the refresh token on every refresh (best practice — a leaked
     * token is only usable until the legitimate user refreshes next, at which
     * point the stolen one stops matching the DB row). The new refresh token
     * is issued with the same window the user originally chose (remember-me
     * vs. default), so the total session is sliding: staying active keeps you
     * signed in; two days/30 days of silence logs you out.
     *
     * Implementation note: refresh tokens are stored as HMAC-SHA-256 hashes
     * (legacy rows may still be in bcrypt format — RefreshTokenHasher.matches
     * accepts both during the migration window). We parse the JWT first
     * (signature-verifies that the token came from us), pull the uid out
     * of its claims, then verify the submitted token against the stored hash.
     */
    @Transactional
    public LoginResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank())
            throw ServiceException.unauth("No refresh token");

        // jwt.parse throws on invalid signature, expired, malformed — so by
        // the time we reach the next line we know the token was issued by us
        // and isn't expired.
        Claims claims;
        try {
            claims = jwt.parse(refreshToken);
        } catch (Exception e) {
            throw ServiceException.unauth("Invalid refresh token");
        }
        if (!"refresh".equals(claims.get("type", String.class)))
            throw ServiceException.unauth("Invalid refresh token");
        Long uid = claims.get("uid", Long.class);
        if (uid == null) throw ServiceException.unauth("Invalid refresh token");

        Credential c = repo.findById(uid)
                .orElseThrow(() -> ServiceException.unauth("Invalid refresh token"));

        // Token must match the stored hash. RefreshTokenHasher accepts both
        // HMAC (new format) and bcrypt (legacy rows from before the migration)
        // so existing sessions keep working through one rotation cycle.
        if (c.getRefreshToken() == null
                || !refreshHasher.matches(refreshToken, c.getRefreshToken())) {
            throw ServiceException.unauth("Invalid refresh token");
        }

        int cookieAge = Boolean.TRUE.equals(c.getRememberMe()) ? refreshRemember : refreshDefault;
        String newAccess  = jwt.issueAccess(c);
        String newRefresh = jwt.issueRefresh(c, cookieAge);
        // Always rotate AND always rewrite as HMAC — this is the path that
        // drains legacy bcrypt rows out of the DB without any explicit
        // migration job: every active session moves to HMAC on its next
        // refresh, dormant sessions drop off naturally when they expire.
        c.setRefreshToken(refreshHasher.hash(newRefresh));
        repo.save(c);
        return new LoginResult(c, newAccess, newRefresh, cookieAge);
    }

    @Transactional
    public void logout(long userId) {
        repo.findById(userId).ifPresent(c -> { c.setRefreshToken(null); repo.save(c); });
    }

    @Transactional
    public boolean activate(String code) {
        return repo.findByActivationCode(code).map(c -> {
            c.setActivated(true);
            c.setActivationCode(null);
            repo.save(c);
            return true;
        }).orElse(false);
    }

    @Transactional
    public void forgotPassword(String emailAddr) {
        // Email enumeration protection: don't tell the caller whether the
        // address is registered. We *silently* no-op for unknown addresses
        // so an attacker probing /forgot-password gets the same 200 OK
        // response either way. The only side-channel left is timing
        // (BCrypt-encoding the reset link path is similar duration to the
        // missing-account path, so the gap is small).
        repo.findByEmail(emailAddr).ifPresent(c -> {
            String selector = UUID.randomUUID().toString();
            String verifier = newResetVerifier();
            String code = selector + "." + verifier;
            c.setResetCode(selector);
            c.setResetCodeHash(hashResetVerifier(verifier));
            c.setResetCodeExpiresAt(LocalDateTime.now().plus(Duration.ofMinutes(resetCodeTtlMinutes)));
            c.setResetCodeAttempts(0);
            repo.save(c);
            String link = frontendBaseUrl + "/reset-password?code=" + code;
            email.send(c.getEmail(), "Reset your password",
                    "Reset link: " + link +
                    "\n\nThis link expires in " + resetCodeTtlMinutes + " minutes.");
        });
    }

    @Transactional
    public boolean resetPassword(String code, String newPassword) {
        PasswordPolicy.validate(newPassword);
        ResetTokenParts parts = parseResetToken(code);
        if (parts == null) return false;

        return repo.findByResetCode(parts.selector()).map(c -> {
            if (c.getResetCodeExpiresAt() == null || c.getResetCodeExpiresAt().isBefore(LocalDateTime.now())) {
                clearResetCode(c);
                repo.save(c);
                return false;
            }

            int attempts = c.getResetCodeAttempts() == null ? 0 : c.getResetCodeAttempts();
            if (attempts >= resetCodeMaxAttempts) {
                clearResetCode(c);
                repo.save(c);
                return false;
            }

            if (c.getResetCodeHash() != null) {
                String submittedHash = parts.verifier() == null ? null : hashResetVerifier(parts.verifier());
                if (!constantTimeEquals(c.getResetCodeHash(), submittedHash)) {
                    attempts++;
                    if (attempts >= resetCodeMaxAttempts) {
                        clearResetCode(c);
                    } else {
                        c.setResetCodeAttempts(attempts);
                    }
                    repo.save(c);
                    return false;
                }
            } else if (parts.verifier() != null) {
                return false;
            }

            c.setPassword(encoder.encode(newPassword));
            c.setRefreshToken(null);
            clearResetCode(c);
            repo.save(c);
            return true;
        }).orElse(false);
    }

    private static void clearResetCode(Credential c) {
        c.setResetCode(null);
        c.setResetCodeHash(null);
        c.setResetCodeExpiresAt(null);
        c.setResetCodeAttempts(null);
    }

    private record ResetTokenParts(String selector, String verifier) {}

    private static ResetTokenParts parseResetToken(String code) {
        if (code == null || code.isBlank()) return null;
        int dot = code.indexOf('.');
        if (dot < 0) return new ResetTokenParts(code, null);
        if (dot == 0 || dot == code.length() - 1 || code.indexOf('.', dot + 1) >= 0) return null;
        return new ResetTokenParts(code.substring(0, dot), code.substring(dot + 1));
    }

    private static String newResetVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashResetVerifier(String verifier) {
        if (verifier == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Transactional
    public boolean changePassword(long userId, String current, String next) {
        PasswordPolicy.validate(next);
        Credential c = repo.findById(userId).orElseThrow(() -> ServiceException.notFound("User not found"));
        if (!encoder.matches(current, c.getPassword()))
            throw ServiceException.unauth("Current password incorrect");
        c.setPassword(encoder.encode(next));
        repo.save(c);
        return true;
    }

    public Credential byId(long id) {
        return repo.findById(id).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    public Credential byEmail(String e) {
        return repo.findByEmail(e).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    public Credential byNickname(String n) {
        return repo.findByNickname(n).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    public boolean emailTaken(String e) { return repo.existsByEmail(e); }
    public boolean nicknameTaken(String n) { return repo.existsByNickname(n); }

    @Transactional
    public void updateEmail(long userId, String newEmail) {
        if (repo.existsByEmail(newEmail)) throw ServiceException.conflict("Email already used");
        Credential c = byId(userId);
        c.setEmail(newEmail);
        repo.save(c);
    }

    @Transactional
    public void updateNickname(long userId, String newNickname) {
        if (repo.existsByNickname(newNickname)) throw ServiceException.conflict("Nickname already used");
        Credential c = byId(userId);
        c.setNickname(newNickname);
        repo.save(c);
    }

    // --- two-step email / password change ----------------------------------
    //
    // The flow:
    //   1. requestEmailChange / requestPasswordChange — generates a 6-digit
    //      code, stores the pending payload (new email / hashed new password)
    //      and a short expiry on the credential row, and emails the code.
    //   2. confirmEmailChange / confirmPasswordChange — verifies the code,
    //      applies the change, clears the pending state. Bad attempts count
    //      up; once they exceed the configured max attempts we wipe the pending state to
    //      defeat brute force, and the user has to start over.
    //
    // Codes are short and high-frequency, so we use SecureRandom and store
    // them in cleartext. The expiry window keeps the brute-force surface
    // tiny (short TTL times limited attempts).

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String newSixDigitCode() {
        // Six digits, zero-padded so leading zeros are preserved.
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    @Transactional
    public void requestEmailChange(long userId, String newEmail) {
        if (newEmail == null || newEmail.isBlank())
            throw ServiceException.invalid("Email cannot be blank");
        if (repo.existsByEmail(newEmail))
            throw ServiceException.conflict("Email already used");

        Credential c = byId(userId);
        if (newEmail.equalsIgnoreCase(c.getEmail()))
            throw ServiceException.invalid("New email is the same as the current one");

        String code = newSixDigitCode();
        c.setPendingEmailCode(code);
        c.setPendingEmailNew(newEmail);
        c.setPendingEmailExpiresAt(LocalDateTime.now().plus(Duration.ofMinutes(accountChangeCodeTtlMinutes)));
        c.setPendingEmailAttempts(0);
        repo.save(c);

        email.send(newEmail,
                "Confirm your new email",
                "Your verification code is: " + code +
                "\n\nThis code expires in " + accountChangeCodeTtlMinutes + " minutes." +
                "\nIf you didn't request this, ignore this email — your address won't change.");
    }

    /**
     * Returns the new email on success, null on failure (caller can decide
     * how to surface bad-code vs expired-code; we throw for those).
     */
    @Transactional
    public String confirmEmailChange(long userId, String submittedCode) {
        Credential c = byId(userId);
        if (c.getPendingEmailCode() == null || c.getPendingEmailNew() == null)
            throw ServiceException.invalid("No pending email change");

        if (c.getPendingEmailExpiresAt() != null && c.getPendingEmailExpiresAt().isBefore(LocalDateTime.now())) {
            clearPendingEmail(c); repo.save(c);
            throw ServiceException.invalid("Verification code expired");
        }

        int attempts = c.getPendingEmailAttempts() == null ? 0 : c.getPendingEmailAttempts();
        if (!c.getPendingEmailCode().equals(submittedCode)) {
            attempts++;
            if (attempts >= accountChangeCodeMaxAttempts) {
                clearPendingEmail(c); repo.save(c);
                throw ServiceException.invalid("Too many bad attempts. Start over.");
            }
            c.setPendingEmailAttempts(attempts);
            repo.save(c);
            throw ServiceException.invalid("Incorrect code");
        }

        // Race: someone else may have grabbed the email between request and confirm.
        if (repo.existsByEmail(c.getPendingEmailNew())) {
            clearPendingEmail(c); repo.save(c);
            throw ServiceException.conflict("Email already used");
        }

        String oldEmail = c.getEmail();
        String newEmail = c.getPendingEmailNew();
        c.setEmail(newEmail);
        clearPendingEmail(c);
        repo.save(c);

        // Notify the OLD address — fire-and-forget so a flaky SMTP doesn't
        // block the user-facing response.
        email.send(oldEmail,
                "Your account email was changed",
                "The email on your account was just changed to " + newEmail + "." +
                "\nIf this wasn't you, contact support immediately.");

        return newEmail;
    }

    private static void clearPendingEmail(Credential c) {
        c.setPendingEmailCode(null);
        c.setPendingEmailNew(null);
        c.setPendingEmailExpiresAt(null);
        c.setPendingEmailAttempts(null);
    }

    @Transactional
    public void requestPasswordChange(long userId, String currentPassword, String newPassword) {
        PasswordPolicy.validate(newPassword);

        Credential c = byId(userId);
        if (!encoder.matches(currentPassword, c.getPassword()))
            throw ServiceException.unauth("Current password incorrect");

        String code = newSixDigitCode();
        c.setPendingPasswordCode(code);
        c.setPendingPasswordNewHash(encoder.encode(newPassword));
        c.setPendingPasswordExpiresAt(LocalDateTime.now().plus(Duration.ofMinutes(accountChangeCodeTtlMinutes)));
        c.setPendingPasswordAttempts(0);
        repo.save(c);

        // Code goes to the *current* address — proves they have account access,
        // so a stolen session can't pivot into a password reset silently.
        email.send(c.getEmail(),
                "Confirm your password change",
                "Your verification code is: " + code +
                "\n\nThis code expires in " + accountChangeCodeTtlMinutes + " minutes." +
                "\nIf you didn't request this, change your password immediately — someone may have access to your session.");
    }

    @Transactional
    public boolean confirmPasswordChange(long userId, String submittedCode) {
        Credential c = byId(userId);
        if (c.getPendingPasswordCode() == null || c.getPendingPasswordNewHash() == null)
            throw ServiceException.invalid("No pending password change");

        if (c.getPendingPasswordExpiresAt() != null && c.getPendingPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            clearPendingPassword(c); repo.save(c);
            throw ServiceException.invalid("Verification code expired");
        }

        int attempts = c.getPendingPasswordAttempts() == null ? 0 : c.getPendingPasswordAttempts();
        if (!c.getPendingPasswordCode().equals(submittedCode)) {
            attempts++;
            if (attempts >= accountChangeCodeMaxAttempts) {
                clearPendingPassword(c); repo.save(c);
                throw ServiceException.invalid("Too many bad attempts. Start over.");
            }
            c.setPendingPasswordAttempts(attempts);
            repo.save(c);
            throw ServiceException.invalid("Incorrect code");
        }

        c.setPassword(c.getPendingPasswordNewHash());
        clearPendingPassword(c);
        repo.save(c);
        return true;
    }

    private static void clearPendingPassword(Credential c) {
        c.setPendingPasswordCode(null);
        c.setPendingPasswordNewHash(null);
        c.setPendingPasswordExpiresAt(null);
        c.setPendingPasswordAttempts(null);
    }

    /** Stateless token validation — used by the gateway on every request. */
    public record ValidationResult(boolean valid, long userId, String emailAddr, String nickname, Role role) {
        public static ValidationResult invalid() { return new ValidationResult(false, 0, "", "", null); }
    }

    public ValidationResult validate(String accessToken) {
        try {
            Claims claims = jwt.parse(accessToken);
            if (claims.getExpiration().toInstant().toEpochMilli() < System.currentTimeMillis())
                return ValidationResult.invalid();
            Long uid = claims.get("uid", Long.class);
            if (uid == null) return ValidationResult.invalid();
            if (claims.get("type") != null) return ValidationResult.invalid();
            // Light DB hit to confirm the user still exists and matches the token's subject.
            Credential c = repo.findById(uid).orElse(null);
            if (c == null || !c.getEmail().equals(claims.getSubject())) return ValidationResult.invalid();
            return new ValidationResult(true, c.getId(), c.getEmail(), c.getNickname(), c.getRole());
        } catch (Exception e) {
            return ValidationResult.invalid();
        }
    }
}
