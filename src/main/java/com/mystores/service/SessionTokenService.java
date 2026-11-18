package com.mystores.service;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/** Signs login sessions so Render process restarts do not invalidate a still-valid browser session. */
@Service
public class SessionTokenService {
    public record Principal(String role, Long customerId) {}

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final long lifetimeSeconds;
    private final SecureRandom random = new SecureRandom();

    public SessionTokenService(Environment env) {
        String configured = env.getProperty("app.security.session-secret", "").trim();
        if (configured.isBlank()) throw new IllegalStateException("Set SESSION_SECRET or OWNER_PASSWORD to sign login sessions.");
        try {
            this.secret = MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        long days = env.getProperty("app.security.session-days", Long.class, 7L);
        this.lifetimeSeconds = Math.max(1, days) * 24 * 60 * 60;
    }

    public String issue(String role, Long customerId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String id = customerId == null ? "-" : customerId.toString();
        String payload = "1|" + role + "|" + id + "|" + (Instant.now().getEpochSecond() + lifetimeSeconds)
                + "|" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }

    public Principal verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw unauthorized();
            }
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 5 || !fields[0].equals("1") || Instant.now().getEpochSecond() >= Long.parseLong(fields[3])) {
                throw unauthorized();
            }
            Long customerId = fields[2].equals("-") ? null : Long.valueOf(fields[2]);
            if (!fields[1].equals("SELLER") && !fields[1].equals("CUSTOMER")) throw unauthorized();
            if (fields[1].equals("CUSTOMER") != (customerId != null)) throw unauthorized();
            return new Principal(fields[1], customerId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign session token", e);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is invalid or expired. Please sign in again.");
    }
}
