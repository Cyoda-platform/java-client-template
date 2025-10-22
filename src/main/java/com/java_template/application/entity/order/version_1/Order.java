package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing confirmed orders with fulfillment status,
 * line items, totals, and customer contact information.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String orderId; // Business ID
    private String orderNumber; // Short ULID
    private String status; // "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines;
    private OrderTotals totals;
    private GuestContact guestContact;

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
    public boolean isValid() {
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
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Integer items; // Total item count
        private Double grand; // Grand total amount
    }

    /**
     * Guest contact information (required for orders)
     */
    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private GuestAddress address;

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Guest address information (required for orders)
     */
    @Data
    public static class GuestAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
