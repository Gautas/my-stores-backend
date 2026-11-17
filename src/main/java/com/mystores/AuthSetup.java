package com.mystores;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class OwnerAccountBootstrap implements ApplicationRunner {
    private final SellerAccountRepository sellers;
    private final BCryptPasswordEncoder encoder;
    private final Environment env;

    OwnerAccountBootstrap(SellerAccountRepository sellers, BCryptPasswordEncoder encoder, Environment env) {
        this.sellers=sellers; this.encoder=encoder; this.env=env;
    }

    @Override public void run(ApplicationArguments args) {
        if (sellers.existsById(1L)) return;
        String email=env.getProperty("app.owner.email","owner@mystores.local").trim().toLowerCase();
        String password=env.getProperty("app.owner.password","change-me-now");
        if(email.isBlank() || password.length()<8) throw new IllegalStateException("Set OWNER_EMAIL and an OWNER_PASSWORD with at least 8 characters before starting the API.");
        SellerAccount owner=new SellerAccount(); owner.id=1L; owner.email=email; owner.passwordHash=encoder.encode(password);
        owner.name=env.getProperty("app.owner.name","Store owner"); owner.passwordChangedAt=java.time.Instant.now();
        sellers.save(owner);
    }
}
