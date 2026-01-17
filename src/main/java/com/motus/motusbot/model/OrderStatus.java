package com.motus.motusbot.model;

import lombok.Getter;

@Getter
public enum OrderStatus {
    AVAILABLE("Доступен"),
    IN_PROGRESS("Выполняется"),
    PAUSED("Пауза"),
    COMPLETED("Выполнен");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public static OrderStatus fromDisplayName(String displayName) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.displayName.equals(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + displayName);
    }
}
