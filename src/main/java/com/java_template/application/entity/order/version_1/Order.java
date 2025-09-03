package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.Instant;
import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String orderId;
    private String orderNumber; // Short ULID
    private List<OrderLine> lines;
    private OrderTotals totals;
    private GuestContact guestContact;
    
    // Auto-generated timestamps
    private Instant createdAt;
    private Instant updatedAt;

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
               lines != null && !lines.isEmpty() &&
               totals != null && totals.isValid() &&
               guestContact != null && guestContact.isValid();
    }

    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;
        
        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   name != null && !name.trim().isEmpty() &&
                   unitPrice != null && unitPrice >= 0 &&
                   qty != null && qty > 0 &&
                   lineTotal != null && lineTotal >= 0;
        }
        
        public void calculateLineTotal() {
            if (unitPrice != null && qty != null) {
                lineTotal = unitPrice * qty;
            }
        }
    }

    @Data
    public static class OrderTotals {
        private Double items;
        private Double grand;
        
        public boolean isValid() {
            return items != null && items >= 0 &&
                   grand != null && grand >= 0;
        }
    }

    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private Address address;
        
        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    @Data
    public static class Address {
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
    
    // Helper methods for business logic
    public boolean hasValidLines() {
        return lines != null && !lines.isEmpty() && 
               lines.stream().allMatch(OrderLine::isValid);
    }
    
    public void calculateTotals() {
        if (lines == null || lines.isEmpty()) {
            totals = new OrderTotals();
            totals.setItems(0.0);
            totals.setGrand(0.0);
            return;
        }
        
        double itemsTotal = lines.stream()
            .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
            .sum();
            
        if (totals == null) {
            totals = new OrderTotals();
        }
        
        totals.setItems(itemsTotal);
        totals.setGrand(itemsTotal); // For demo, grand total equals items total
        updatedAt = Instant.now();
    }
}
