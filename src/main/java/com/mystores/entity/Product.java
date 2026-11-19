package com.mystores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String name;
    @Column(length=3000) public String description;
    @Column(nullable=false) public BigDecimal price;
    public int stock;
    public String imageUrl;
    public String videoUrl;
    @ManyToOne(optional=false) public Store store;
    public boolean active = true;

    public boolean inStock() { return stock > 0; }
}
