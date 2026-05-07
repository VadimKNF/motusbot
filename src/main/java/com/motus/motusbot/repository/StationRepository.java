package com.motus.motusbot.repository;

import com.motus.motusbot.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationRepository extends JpaRepository<Station, UUID> {
    Optional<Station> findByTelegramId(Long telegramId);

    @Query("SELECT s FROM Station s LEFT JOIN FETCH s.services WHERE s.telegramId = :telegramId")
    Optional<Station> findByTelegramIdWithServices(@Param("telegramId") Long telegramId);

    @Query("SELECT s FROM Station s JOIN s.services svc WHERE svc.id = :serviceId")
    List<Station> findByServiceId(@Param("serviceId") UUID serviceId);
}
