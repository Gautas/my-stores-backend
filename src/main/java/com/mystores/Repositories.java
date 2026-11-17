package com.mystores;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface StoreRepository extends JpaRepository<Store,Long> { List<Store> findByActiveTrue(); }
interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findByActiveTrueAndStoreId(Long storeId);
    List<Product> findByStoreId(Long storeId);
    List<Product> findByActiveTrue();
    Optional<Product> findByIdAndActiveTrue(Long id);
}
interface CustomerRepository extends JpaRepository<Customer,Long> { Optional<Customer> findByEmailIgnoreCase(String email); }
interface OrderRepository extends JpaRepository<OrderRecord,Long> { List<OrderRecord> findByCustomerIdOrderByIdDesc(Long customerId); }
