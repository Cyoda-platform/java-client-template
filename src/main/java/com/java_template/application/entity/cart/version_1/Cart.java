package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.Instant;
import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String cartId;
    private List<CartLine> lines;
    private Integer totalItems;
    private Double grandTotal;
    
    // Optional fields
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
        return cartId != null && !cartId.trim().isEmpty() &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0;
    }

    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
        
        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   name != null && !name.trim().isEmpty() &&
                   price != null && price >= 0 &&
                   qty != null && qty > 0;
        }
        
        public Double getLineTotal() {
            if (price != null && qty != null) {
                return price * qty;
            }
            return 0.0;
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
    public boolean hasItems() {
        return lines != null && !lines.isEmpty() && totalItems != null && totalItems > 0;
    }
    
    public boolean hasValidGuestContact() {
        return guestContact != null && guestContact.isValid();
    }
    
    public void recalculateTotals() {
        if (lines == null) {
            totalItems = 0;
            grandTotal = 0.0;
            return;
        }
        
        int itemCount = 0;
        double total = 0.0;
        
        for (CartLine line : lines) {
            if (line != null && line.getQty() != null && line.getPrice() != null) {
                itemCount += line.getQty();
                total += line.getLineTotal();
            }
        }
        
        totalItems = itemCount;
        grandTotal = total;
        updatedAt = Instant.now();
    }
}
