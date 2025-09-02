package com.java_template.application.entity.cart.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart entity representing a shopping cart for anonymous users with line items and totals.
 */
public class Cart implements CyodaEntity {

    @JsonProperty("cartId")
    private String cartId;

    @JsonProperty("lines")
    private List<CartLine> lines;

    @JsonProperty("totalItems")
    private Integer totalItems;

    @JsonProperty("grandTotal")
    private Double grandTotal;

    @JsonProperty("guestContact")
    private GuestContact guestContact;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Cart() {}

    // Constructor with required fields
    public Cart(String cartId, List<CartLine> lines, Integer totalItems, Double grandTotal) {
        this.cartId = cartId;
        this.lines = lines;
        this.totalItems = totalItems;
        this.grandTotal = grandTotal;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Cart");
        modelSpec.setVersion(1);
        return new OperationSpecification.Entity(modelSpec, "Cart");
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return cartId != null && !cartId.trim().isEmpty() &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0;
    }

    // Getters and setters
    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public List<CartLine> getLines() { return lines; }
    public void setLines(List<CartLine> lines) { this.lines = lines; }

    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }

    public Double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }

    public GuestContact getGuestContact() { return guestContact; }
    public void setGuestContact(GuestContact guestContact) { this.guestContact = guestContact; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Inner classes
    public static class CartLine {
        @JsonProperty("sku")
        private String sku;

        @JsonProperty("name")
        private String name;

        @JsonProperty("price")
        private Double price;

        @JsonProperty("qty")
        private Integer qty;

        @JsonProperty("lineTotal")
        private Double lineTotal;

        public CartLine() {}

        public CartLine(String sku, String name, Double price, Integer qty) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.qty = qty;
            this.lineTotal = price * qty;
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }

        public Double getLineTotal() { return lineTotal; }
        public void setLineTotal(Double lineTotal) { this.lineTotal = lineTotal; }

        public void calculateLineTotal() {
            if (price != null && qty != null) {
                this.lineTotal = price * qty;
            }
        }
    }

    public static class GuestContact {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        private GuestAddress address;

        public GuestContact() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public GuestAddress getAddress() { return address; }
        public void setAddress(GuestAddress address) { this.address = address; }

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    public static class GuestAddress {
        @JsonProperty("line1")
        private String line1;

        @JsonProperty("city")
        private String city;

        @JsonProperty("postcode")
        private String postcode;

        @JsonProperty("country")
        private String country;

        public GuestAddress() {}

        public String getLine1() { return line1; }
        public void setLine1(String line1) { this.line1 = line1; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }

    // Helper methods for cart calculations
    public void recalculateTotals() {
        if (lines != null) {
            this.totalItems = lines.stream().mapToInt(CartLine::getQty).sum();
            this.grandTotal = lines.stream().mapToDouble(CartLine::getLineTotal).sum();
        } else {
            this.totalItems = 0;
            this.grandTotal = 0.0;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public CartLine findLineBySkuOrNull(String sku) {
        if (lines == null || sku == null) {
            return null;
        }
        return lines.stream()
                .filter(line -> sku.equals(line.getSku()))
                .findFirst()
                .orElse(null);
    }

    public boolean removeLine(String sku) {
        if (lines == null || sku == null) {
            return false;
        }
        boolean removed = lines.removeIf(line -> sku.equals(line.getSku()));
        if (removed) {
            recalculateTotals();
        }
        return removed;
    }
}
