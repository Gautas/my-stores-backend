package com.mystores;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@Service
class PasswordResetService {
    private final CustomerRepository customers;
    private final SellerAccountRepository sellers;
    private final PasswordResetTokenRepository tokens;
    private final BCryptPasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final Environment env;
    private final SecureRandom random=new SecureRandom();

    PasswordResetService(CustomerRepository customers, SellerAccountRepository sellers, PasswordResetTokenRepository tokens,
                         BCryptPasswordEncoder encoder, ObjectProvider<JavaMailSender> mailSenders, Environment env) {
        this.customers=customers; this.sellers=sellers; this.tokens=tokens; this.encoder=encoder; this.mailSenders=mailSenders; this.env=env;
    }

    void requestReset(String requestedRole,String requestedEmail) {
        String role=normalizeRole(requestedRole);
        String email=requestedEmail.trim().toLowerCase(Locale.ROOT);
        String smtpHost=env.getProperty("spring.mail.host","").trim();
        String from=env.getProperty("app.mail.from","").trim();
        JavaMailSender sender=mailSenders.getIfAvailable();
        if(smtpHost.isBlank() || from.isBlank() || sender==null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password reset email is not configured on the server.");

        boolean accountExists=role.equals("SELLER")
            ? sellers.findByEmailIgnoreCase(email).isPresent()
            : customers.findByEmailIgnoreCase(email).isPresent();
        if(!accountExists) return;

        tokens.deleteAllByEmailIgnoreCaseAndRoleAndUsedFalse(email,role);
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken token=new PasswordResetToken(); token.tokenHash=sha256(raw); token.email=email; token.role=role;
        int expiryMinutes=env.getProperty("app.security.reset-minutes",Integer.class,15);
        token.expiresAt=Instant.now().plus(Math.max(5,expiryMinutes),ChronoUnit.MINUTES); tokens.save(token);
        String base=env.getProperty("app.security.reset-base-url","https://stores-ebaf.onrender.com").replaceAll("/$","");
        String link=base+"/?resetToken="+URLEncoder.encode(raw,StandardCharsets.UTF_8)+"&role="+role.toLowerCase(Locale.ROOT);
        try {
            SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(from); message.setTo(email);
            message.setSubject("Reset your My Stores password");
            message.setText("Use this link to reset your My Stores password. It expires in "+Math.max(5,expiryMinutes)+" minutes:\n\n"+link+"\n\nIf you did not request this, ignore this email.");
            sender.send(message);
        } catch(Exception ex) {
            tokens.delete(token);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"The reset email could not be sent. Check the SMTP settings.");
        }
    }

    void resetPassword(String rawToken,String requestedRole,String newPassword) {
        String role=normalizeRole(requestedRole);
        PasswordResetToken token=tokens.findByTokenHashAndUsedFalse(sha256(rawToken)).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"This reset link is invalid or has already been used."));
        if(!token.role.equals(role) || !token.expiresAt.isAfter(Instant.now())) {
            token.used=true; tokens.save(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This reset link has expired. Request a new one.");
        }
        if(role.equals("SELLER")) {
            SellerAccount owner=sellers.findByEmailIgnoreCase(token.email).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Account no longer exists."));
            owner.passwordHash=encoder.encode(newPassword); owner.passwordChangedAt=Instant.now(); sellers.save(owner);
        } else {
            Customer customer=customers.findByEmailIgnoreCase(token.email).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Account no longer exists."));
            customer.passwordHash=encoder.encode(newPassword); customer.passwordChangedAt=Instant.now(); customers.save(customer);
        }
        token.used=true; tokens.save(token);
        tokens.deleteAllByEmailIgnoreCaseAndRoleAndUsedFalse(token.email,role);
    }

    private String normalizeRole(String role) {
        String value=role==null?"":role.toUpperCase(Locale.ROOT);
        if(!value.equals("SELLER") && !value.equals("CUSTOMER")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown account type.");
        return value;
    }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException("SHA-256 is unavailable",ex); }
    }
}
