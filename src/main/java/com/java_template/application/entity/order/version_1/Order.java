package com.java_template.application.entity.order.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order entity representing a customer order created from successful cart checkout.
 */
public class Order implements CyodaEntity {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderNumber")
    private String orderNumber;

    @JsonProperty("lines")
    private List<OrderLine> lines;

    @JsonProperty("totals")
    private OrderTotals totals;

    @JsonProperty("guestContact")
    private GuestContact guestContact;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Order() {}

    // Constructor with required fields
    public Order(String orderId, String orderNumber, List<OrderLine> lines, OrderTotals totals, GuestContact guestContact) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.lines = lines;
        this.totals = totals;
        this.guestContact = guestContact;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Order");
        modelSpec.setVersion(1);
        return new OperationSpecification.Entity(modelSpec, "Order");
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null && totals.isValid() &&
               guestContact != null && guestContact.isValid();
    }

    // Getters and setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> lines) { this.lines = lines; }

    public OrderTotals getTotals() { return totals; }
    public void setTotals(OrderTotals totals) { this.totals = totals; }

    public GuestContact getGuestContact() { return guestContact; }
    public void setGuestContact(GuestContact guestContact) { this.guestContact = guestContact; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Helper method to update timestamp
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    // Inner classes
    public static class OrderLine {
        @JsonProperty("sku")
        private String sku;

        @JsonProperty("name")
        private String name;

        @JsonProperty("unitPrice")
        private Double unitPrice;

        @JsonProperty("qty")
        private Integer qty;

        @JsonProperty("lineTotal")
        private Double lineTotal;

        public OrderLine() {}

        public OrderLine(String sku, String name, Double unitPrice, Integer qty) {
            this.sku = sku;
            this.name = name;
            this.unitPrice = unitPrice;
            this.qty = qty;
            this.lineTotal = unitPrice * qty;
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }

        public Double getLineTotal() { return lineTotal; }
        public void setLineTotal(Double lineTotal) { this.lineTotal = lineTotal; }

        public void calculateLineTotal() {
            if (unitPrice != null && qty != null) {
                this.lineTotal = unitPrice * qty;
            }
        }

        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   name != null && !name.trim().isEmpty() &&
                   unitPrice != null && unitPrice > 0 &&
                   qty != null && qty > 0 &&
                   lineTotal != null && lineTotal > 0;
        }
    }

    public static class OrderTotals {
        @JsonProperty("items")
        private Integer items;

        @JsonProperty("grand")
        private Double grand;

        public OrderTotals() {}

        public OrderTotals(Integer items, Double grand) {
            this.items = items;
            this.grand = grand;
        }

        public Integer getItems() { return items; }
        public void setItems(Integer items) { this.items = items; }

        public Double getGrand() { return grand; }
        public void setGrand(Double grand) { this.grand = grand; }

        public boolean isValid() {
            return items != null && items > 0 &&
                   grand != null && grand > 0;
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

    // Helper methods for order calculations
    public void recalculateTotals() {
        if (lines != null) {
            int totalItems = lines.stream().mapToInt(OrderLine::getQty).sum();
            double grandTotal = lines.stream().mapToDouble(OrderLine::getLineTotal).sum();
            this.totals = new OrderTotals(totalItems, grandTotal);
        } else {
            this.totals = new OrderTotals(0, 0.0);
        }
        this.updatedAt = LocalDateTime.now();
    }
}
