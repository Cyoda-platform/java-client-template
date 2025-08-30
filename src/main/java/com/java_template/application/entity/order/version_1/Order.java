package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Technical/Business identifiers
    private String orderId;       // serialized UUID or unique order identifier
    private String orderNumber;
    private String status;

    // Timestamps (ISO strings)
    private String createdAt;
    private String updatedAt;

    // Guest contact and shipping/billing info
    private GuestContact guestContact;

    // Order lines
    private List<Line> lines;

    // Totals
    private Totals totals;

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate core required fields
        if (orderId == null || orderId.isBlank()) return false;
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate guest contact minimally
        if (guestContact == null) return false;
        if (guestContact.getEmail() == null || guestContact.getEmail().isBlank()) return false;
        // address presence
        if (guestContact.getAddress() == null) return false;

        // Validate totals
        if (totals == null) return false;
        if (totals.getGrand() == null) return false;

        // Validate lines
        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getUnitPrice() == null) return false;
            if (l.getQty() == null || l.getQty() <= 0) return false;
            // lineTotal can be null (could be computed), but if present validate non-negative
            if (l.getLineTotal() != null && l.getLineTotal() < 0) return false;
        }

        return true;
    }

    @Data
    public static class GuestContact {
        private Address address;
        private String email;
        private String name;
        private String phone;
    }

    @Data
    public static class Address {
        private String city;
        private String country;
        private String line1;
        private String postcode;
    }

    @Data
    public static class Line {
        private String sku;
        private String name;
        private Integer qty;
        private Double unitPrice;
        private Double lineTotal;
    }

    @Data
    public static class Totals {
        private Double grand;
        private Double items;
    }
}