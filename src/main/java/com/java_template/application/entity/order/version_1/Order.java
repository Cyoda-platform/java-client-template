package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing customer orders with line items,
 * totals, guest contact information, and order lifecycle management.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String orderId; // required, unique business identifier
    private String orderNumber; // required, short ULID for customer reference
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
    public boolean isValid(EntityMetadata metadata) {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null;
    }

    /**
     * Order line item with product information and pricing
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
        private Integer items; // total number of items
        private Double grand; // grand total amount
    }

    /**
     * Guest contact information (required for orders)
     */
    @Data
    public static class GuestContact {
        private String name; // required
        private String email;
        private String phone;
        private GuestAddress address; // required
    }

    /**
     * Guest address information (required for shipping)
     */
    @Data
    public static class GuestAddress {
        private String line1; // required
        private String city; // required
        private String postcode; // required
        private String country; // required
    }
}
