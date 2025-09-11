package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Represents a customer order created from a paid cart.
 * 
 * Entity state is managed by the workflow system:
 * - WAITING_TO_FULFILL: Order created, waiting to start fulfillment
 * - PICKING: Order is being picked in warehouse
 * - WAITING_TO_SEND: Order picked, waiting to ship
 * - SENT: Order has been shipped
 * - DELIVERED: Order has been delivered
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String orderId;             // Required, unique
    private String orderNumber;         // Required, unique: Short ULID for customer reference
    private List<OrderLine> lines;      // Required: Order line items
    private OrderTotals totals;         // Required: Order totals
    private OrderGuestContact guestContact;  // Required: Guest contact information

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
        if (orderId == null || orderId.trim().isEmpty()) {
            return false;
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return false;
        }
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        if (totals == null) {
            return false;
        }
        if (guestContact == null) {
            return false;
        }

        // Validate guest contact required fields
        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return false;
        }
        if (guestContact.getAddress() == null || 
            guestContact.getAddress().getLine1() == null || 
            guestContact.getAddress().getLine1().trim().isEmpty()) {
            return false;
        }

        // Validate lines
        for (OrderLine line : lines) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return false;
            }
            if (line.getQty() == null || line.getQty() <= 0) {
                return false;
            }
            if (line.getUnitPrice() == null || line.getUnitPrice() < 0) {
                return false;
            }
            if (line.getLineTotal() == null || line.getLineTotal() < 0) {
                return false;
            }
            // Validate line total calculation
            double expectedLineTotal = line.getUnitPrice() * line.getQty();
            if (Math.abs(line.getLineTotal() - expectedLineTotal) > 0.01) {
                return false;
            }
        }

        // Validate totals
        if (totals.getItems() == null || totals.getGrand() == null) {
            return false;
        }

        // Validate total items calculation
        int calculatedTotalItems = lines.stream()
                .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                .sum();
        if (!totals.getItems().equals(calculatedTotalItems)) {
            return false;
        }

        // Validate grand total calculation
        double calculatedGrandTotal = lines.stream()
                .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                .sum();
        if (Math.abs(totals.getGrand() - calculatedGrandTotal) > 0.01) {
            return false;
        }

        return true;
    }

    /**
     * Order line item with sku, name, unitPrice, qty, lineTotal
     */
    @Data
    public static class OrderLine {
        private String sku;         // Product SKU
        private String name;        // Product name
        private Double unitPrice;   // Unit price at time of order
        private Integer qty;        // Quantity ordered
        private Double lineTotal;   // Line total (unitPrice * qty)
    }

    /**
     * Order totals with items and grand total
     */
    @Data
    public static class OrderTotals {
        private Integer items;      // Total number of items
        private Double grand;       // Grand total amount
    }

    /**
     * Guest contact information (name required, email/phone optional, address.line1 required)
     */
    @Data
    public static class OrderGuestContact {
        private String name;        // Required
        private String email;       // Optional
        private String phone;       // Optional
        private OrderAddress address;   // Required with line1
    }

    /**
     * Guest address information
     */
    @Data
    public static class OrderAddress {
        private String line1;       // Required
        private String city;
        private String postcode;
        private String country;
    }
}
