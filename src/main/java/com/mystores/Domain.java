package com.mystores;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
class Store {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) String name;
    String description;
    String location;
    boolean active = true;
}

@Entity
class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) String name;
    @Column(length=3000) String description;
    @Column(nullable=false) BigDecimal price;
    int stock;
    String imageUrl;
    @ManyToOne(optional=false) Store store;
    boolean active = true;
    boolean inStock() { return stock > 0; }
}

@Entity
class Customer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,unique=true) String email;
    @Column(nullable=false) String passwordHash;
    String name;
    Instant passwordChangedAt;
}

@Entity
class SellerAccount {
    @Id Long id = 1L;
    @Column(nullable=false,unique=true) String email;
    @Column(nullable=false) String passwordHash;
    @Column(nullable=false) String name = "Store owner";
    Instant passwordChangedAt;
}

@Entity
class PasswordResetToken {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,unique=true,length=64) String tokenHash;
    @Column(nullable=false) String email;
    @Column(nullable=false) String role;
    @Column(nullable=false) Instant expiresAt;
    @Column(nullable=false) boolean used = false;
}

@Entity
class OrderRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(optional=false) Store store;
    @ManyToOne(optional=false) Customer customer;
    @ManyToOne(optional=false) Product product;
    int quantity;
    @Column(nullable=false) BigDecimal total;
    String status = "PLACED";
}
