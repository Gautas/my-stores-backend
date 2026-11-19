package com.mystores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;

@Entity
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String name;
    @Column(length=3000) public String description;
    @Column(nullable=false) public BigDecimal price;
    public int stock;
    public String imageUrl;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="product_images", joinColumns=@JoinColumn(name="product_id"))
    @Column(name="image_url", length=1000)
    public List<String> imageUrls = new ArrayList<>();
    public String videoUrl;
    @ManyToOne(optional=false) public Store store;
    public boolean active = true;

    public boolean inStock() { return stock > 0; }
}
