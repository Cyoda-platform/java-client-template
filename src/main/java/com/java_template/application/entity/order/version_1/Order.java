package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing customer orders with line items,
 * totals, and guest contact information for fulfillment processing.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String orderId; // Business ID - required, unique
    private String orderNumber; // Short ULID - required
    private String status; // "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines; // required
    private OrderTotals totals; // required
    private GuestContact guestContact; // required

    // Optional fields
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
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    /**
     * Order line item representing a product in the order
     */
    @Data
    public static class OrderLine {
        private String sku; // Product SKU
        private String name; // Product name
        private Double unitPrice; // Unit price at time of order
        private Integer qty; // Quantity ordered
        private Double lineTotal; // Line total (unitPrice * qty)
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Double items; // Total for all items
        private Double grand; // Grand total (items + taxes + shipping - discounts)
    }

    /**
     * Guest contact information for order fulfillment
     */
    @Data
    public static class GuestContact {
        private String name; // required
        private String email;
        private String phone;
        private GuestAddress address; // required

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Guest address information for shipping
     */
    @Data
    public static class GuestAddress {
        private String line1; // required
        private String city; // required
        private String postcode; // required
        private String country; // required

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
