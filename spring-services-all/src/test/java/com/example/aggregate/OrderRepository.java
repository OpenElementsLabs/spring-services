package com.example.aggregate;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The aggregate test application's own repository. */
public interface OrderRepository extends JpaRepository<Order, UUID> {}
