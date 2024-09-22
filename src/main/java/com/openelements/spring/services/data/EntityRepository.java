package com.openelements.spring.services.data;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface EntityRepository<E extends DbEntity> extends JpaRepository<E, UUID> {
}
