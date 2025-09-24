package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Order management for OMS
 * 
 * Represents a confirmed order with ULID order number, line items,
 * totals, guest contact, and status tracking through order lifecycle.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId; // required, unique business identifier

    // Required core fields
    private String orderNumber; // short ULID for customer reference
    private String status; // "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines; // order line items
    private OrderTotals totals; // order totals
    private OrderGuestContact guestContact; // required guest contact

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
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    /**
     * Order line item with product details, pricing, and quantities
     */
    @Data
    public static class OrderLine {
        private String sku; // product SKU
        private String name; // product name
        private Double unitPrice; // unit price at time of order
        private Integer qty; // quantity ordered
        private Double lineTotal; // calculated line total (unitPrice * qty)
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Integer items; // total quantity of items
        private Double grand; // grand total amount
    }

    /**
     * Guest contact information (required for orders)
     */
    @Data
    public static class OrderGuestContact {
        private String name; // required
        private String email;
        private String phone;
        private OrderGuestAddress address; // required

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Guest address information (required for orders)
     */
    @Data
    public static class OrderGuestAddress {
        private String line1; // required
        private String line2;
        private String city; // required
        private String state;
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
