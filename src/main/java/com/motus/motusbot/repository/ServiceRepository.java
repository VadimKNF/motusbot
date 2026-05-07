package com.motus.motusbot.repository;

import com.motus.motusbot.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, java.util.UUID> {
    List<Service> findAllByOrderByNameAsc();

    Optional<Service> findFirstByName(String name);
}
