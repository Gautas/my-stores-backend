package com.mystores;

import jakarta.persistence.*;
import java.math.BigDecimal;

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
