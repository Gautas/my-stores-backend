package com.mystores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Entity
public class OrderRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) public Store store;
    @ManyToOne(optional=false) public Customer customer;
    @ManyToOne(optional=false) public Product product;
    public int quantity;
    @Column(nullable=false) public BigDecimal total;
    public String status = "PLACED";
}
