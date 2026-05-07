package com.motus.motusbot.repository;

import com.motus.motusbot.model.Car;
import com.motus.motusbot.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarRepository extends JpaRepository<Car, java.util.UUID> {
    List<Car> findByClient(Client client);
}
