package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing confirmed orders with line items,
 * totals, guest contact information, and fulfillment status tracking.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String orderId; // required, unique business identifier
    private String orderNumber; // required, short ULID for customer reference
    private String status; // required: "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines; // required: order line items (snapshot from cart)
    private OrderTotals totals; // required: order totals
    private GuestContact guestContact; // required: customer contact information

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
        private String sku; // product SKU
        private String name; // product name (snapshot)
        private Double unitPrice; // unit price at time of order
        private Integer qty; // quantity ordered
        private Double lineTotal; // calculated line total (unitPrice * qty)
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Double items; // total value of items
        private Double grand; // grand total (same as items for demo)
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
