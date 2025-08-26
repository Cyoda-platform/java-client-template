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
    private Address billingAddress;
    private String currency;
    private String customerId; // serialized UUID or business id as String
    private String fulfillmentStatus;
    private List<Item> items;
    private String notes;
    private String orderNumber;
    private String paymentStatus;
    private String placedAt;
    private Address shippingAddress;
    private Double totalAmount;
    private String updatedAt;

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
        // Basic required string checks
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (currency == null || currency.isBlank()) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;
        if (placedAt == null || placedAt.isBlank()) return false;

        // Items validation
        if (items == null || items.isEmpty()) return false;
        double computedTotal = 0.0;
        for (Item it : items) {
            if (it == null) return false;
            boolean hasProductRef = !(it.getSku() == null || it.getSku().isBlank())
                    || !(it.getProductBusinessId() == null || it.getProductBusinessId().isBlank());
            if (!hasProductRef) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getUnitPrice() == null || it.getUnitPrice() < 0) return false;
            computedTotal += it.getQuantity() * it.getUnitPrice();
        }

        if (totalAmount == null) return false;
        // Allow small rounding differences
        if (Math.abs(totalAmount - computedTotal) > 0.01) return false;

        // Address validations (if present)
        if (shippingAddress != null && !shippingAddress.isValid()) return false;
        if (billingAddress != null && !billingAddress.isValid()) return false;

        return true;
    }

    @Data
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;

        public boolean isValid() {
            // Ensure core address fields are present
            if (street == null || street.isBlank()) return false;
            if (city == null || city.isBlank()) return false;
            if (country == null || country.isBlank()) return false;
            if (postalCode == null || postalCode.isBlank()) return false;
            return true;
        }
    }

    @Data
    public static class Item {
        private String productBusinessId; // product business id or SKU reference as String
        private Integer quantity;
        private String sku;
        private Double unitPrice;
    }
}