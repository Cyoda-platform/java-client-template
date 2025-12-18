package com.example.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Shopping cart for OMS
 * States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    // Business ID
    private String cartId;

    // Status: NEW | ACTIVE | CHECKING_OUT | CONVERTED
    private String status;

    // Cart lines
    private List<CartLine> lines;

    // Totals
    private Integer totalItems;
    private Double grandTotal;

    // Guest contact information
    private GuestContact guestContact;

    // Metadata
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
        return cartId != null && !cartId.isBlank() && status != null && !status.isBlank();
    }

    /**
     * Cart line item
     */
    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
        private Double lineTotal;
    }

    /**
     * Guest contact information
     */
    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private Address address;
    }

    /**
     * Address information
     */
    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}

