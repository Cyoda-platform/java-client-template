package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing a customer order with line items, totals, and guest contact information.
 * Status is managed by workflow state, not stored as a business field.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId;
    
    // Required core fields
    private String orderNumber;
    private List<OrderLine> lines;
    private Totals totals;
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
    public boolean isValid() {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               lines != null && totals != null && guestContact != null;
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

