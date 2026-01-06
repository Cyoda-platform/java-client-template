package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Customer Order for OMS
 * States: WAITING_TO_FULFILL -> PICKING -> WAITING_TO_SEND -> SENT -> DELIVERED
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId;

    // Order number (short ULID)
    private String orderNumber;

    // Status: WAITING_TO_FULFILL | PICKING | WAITING_TO_SEND | SENT | DELIVERED
    private String status;

    // Order lines
    private List<OrderLine> lines;

    // Totals
    private Totals totals;

    // Guest contact information
    private GuestContact guestContact;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return orderId != null && !orderId.isBlank() && orderNumber != null && !orderNumber.isBlank() &&
               status != null && !status.isBlank() && lines != null && !lines.isEmpty() &&
               guestContact != null;
    }

    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;
    }

    @Data
    public static class Totals {
        private Integer items;
        private Double grand;
    }

    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private Address address;
    }

    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}

