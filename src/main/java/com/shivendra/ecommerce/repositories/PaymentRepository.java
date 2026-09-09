package com.shivendra.ecommerce.repositories;

import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrder(Order order);

    Optional<Payment> findByProviderReferenceId(String providerReferenceId);
}
