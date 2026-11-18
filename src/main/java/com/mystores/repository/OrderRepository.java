package com.mystores.repository;

import com.mystores.entity.OrderRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderRecord,Long> {
    List<OrderRecord> findByCustomerIdOrderByIdDesc(Long customerId);
}
