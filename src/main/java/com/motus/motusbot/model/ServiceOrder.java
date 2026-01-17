package com.motus.motusbot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "service_order")
public class ServiceOrder {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "service_time", nullable = false)
    private LocalDateTime serviceTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.AVAILABLE;

    @Column(name = "work_description", nullable = false)
    private String workDescription;

    @Column(name = "min_price", precision = 19, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "actual_price", precision = 19, scale = 2)
    private BigDecimal actualPrice;

    @Column(name = "cancellation_reason")
    private String cancellation_reason;

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice != null
                ? minPrice.setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    public void setActualPrice(BigDecimal actualPrice) {
        this.actualPrice = actualPrice != null
                ? actualPrice.setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    public ServiceOrder() {}

    public ServiceOrder(LocalDateTime serviceTime, Car car, Client client, Station station, Service service) {
        this.serviceTime = serviceTime;
        this.car = car;
        this.client = client;
        this.station = station;
        this.service = service;
    }
}
