package com.motus.motusbot.repository;

import com.motus.motusbot.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, java.util.UUID> {
    Optional<Client> findByTelegramId(Long telegramId);
}
