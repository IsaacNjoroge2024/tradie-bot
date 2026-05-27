package com.tradie.common.repository;

import com.tradie.common.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIbOrderId(Integer ibOrderId);

    List<Order> findBySignalId(UUID signalId);

    List<Order> findByStatus(Order.OrderStatus status);

    List<Order> findByStatusIn(List<Order.OrderStatus> statuses);

    List<Order> findTop20ByOrderByCreatedAtDesc();
}
