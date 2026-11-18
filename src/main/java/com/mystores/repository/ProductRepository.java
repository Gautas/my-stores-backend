package com.mystores.repository;

import com.mystores.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findByActiveTrueAndStoreId(Long storeId);
    List<Product> findByStoreId(Long storeId);
    List<Product> findByActiveTrue();
    Optional<Product> findByIdAndActiveTrue(Long id);
}
