package com.example.app;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The consumer application's own Spring Data repository. Discovered only if the library's
 * auto-configuration leaves Boot's default repository scan (over the application package) intact.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {}
