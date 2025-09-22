package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Customer order for OMS
 * 
 * Represents a confirmed order created from a paid cart.
 * Supports workflow states: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId;
    
    // Required fields
    private String orderNumber; // short ULID
    private String status; // "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines;
    private OrderTotals totals;
    private OrderGuestContact guestContact;
    
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
               status != null && isValidStatus(status) &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    private boolean isValidStatus(String status) {
        return "WAITING_TO_FULFILL".equals(status) || 
               "PICKING".equals(status) || 
               "WAITING_TO_SEND".equals(status) || 
               "SENT".equals(status) || 
               "DELIVERED".equals(status);
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
     * Order totals
     */
    @Data
    public static class OrderTotals {
        private Integer items; // total item count
        private Double grand; // grand total amount
    }

    /**
     * Guest contact information for the order (required)
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
