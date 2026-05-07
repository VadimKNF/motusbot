package com.motus.motusbot.repository;

import com.motus.motusbot.model.OrderStatus;
import com.motus.motusbot.model.ServiceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

    @Query("SELECT o FROM ServiceOrder o " +
           "LEFT JOIN FETCH o.service " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.car " +
           "WHERE o.id = :id")
    java.util.Optional<ServiceOrder> findByIdWithServiceAndClientAndCar(@Param("id") UUID id);

    @Query("SELECT o FROM ServiceOrder o " +
           "LEFT JOIN FETCH o.station " +
           "LEFT JOIN FETCH o.client " +
           "WHERE o.id = :id")
    java.util.Optional<ServiceOrder> findByIdWithStationAndClient(@Param("id") UUID id);

    @Query("SELECT DISTINCT o FROM ServiceOrder o " +
           "LEFT JOIN FETCH o.service " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.car " +
           "WHERE o.status = :status AND o.service.id IN :serviceIds " +
           "ORDER BY o.createdAt")
    List<ServiceOrder> findByStatusAndServiceIdIn(
            @Param("status") OrderStatus status,
            @Param("serviceIds") List<UUID> serviceIds);
}
