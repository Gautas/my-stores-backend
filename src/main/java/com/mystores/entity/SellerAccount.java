package com.mystores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class SellerAccount {
    @Id public Long id = 1L;
    @Column(nullable=false,unique=true) public String email;
    @Column(nullable=false) public String passwordHash;
    @Column(nullable=false) public String name = "Store owner";
    public Instant passwordChangedAt;
}
