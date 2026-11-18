package com.mystores.repository;

import com.mystores.entity.SellerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SellerAccountRepository extends JpaRepository<SellerAccount,Long> {
    Optional<SellerAccount> findByEmailIgnoreCase(String email);
}
