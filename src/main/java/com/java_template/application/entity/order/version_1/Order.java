package com.java_template.application.entity.order.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Order entity representing a confirmed order after successful payment processing.
 */
public class Order implements CyodaEntity {

    @JsonProperty("orderId")
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @JsonProperty("orderNumber")
    @NotBlank(message = "Order number is required")
    private String orderNumber;

    @JsonProperty("lines")
    @NotNull(message = "Lines are required")
    @Valid
    private List<OrderLine> lines;

    @JsonProperty("totals")
    @NotNull(message = "Totals are required")
    @Valid
    private OrderTotals totals;

    @JsonProperty("guestContact")
    @NotNull(message = "Guest contact is required")
    @Valid
    private GuestContact guestContact;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Order() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

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
               totals != null &&
               guestContact != null;
    }

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
    }

    public OrderTotals getTotals() {
        return totals;
    }

    public void setTotals(OrderTotals totals) {
        this.totals = totals;
    }

    public GuestContact getGuestContact() {
        return guestContact;
    }

    public void setGuestContact(GuestContact guestContact) {
        this.guestContact = guestContact;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", totals=" + totals +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Order line item representing a product in the order.
     */
    public static class OrderLine {
        @JsonProperty("sku")
        @NotBlank(message = "SKU is required")
        private String sku;

        @JsonProperty("name")
        @NotBlank(message = "Product name is required")
        private String name;

        @JsonProperty("unitPrice")
        @NotNull(message = "Unit price is required")
        @Min(value = 0, message = "Unit price must be non-negative")
        private BigDecimal unitPrice;

        @JsonProperty("qty")
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer qty;

        @JsonProperty("lineTotal")
        @NotNull(message = "Line total is required")
        @Min(value = 0, message = "Line total must be non-negative")
        private BigDecimal lineTotal;

        // Default constructor
        public OrderLine() {}

        // Constructor with all fields
        public OrderLine(String sku, String name, BigDecimal unitPrice, Integer qty, BigDecimal lineTotal) {
            this.sku = sku;
            this.name = name;
            this.unitPrice = unitPrice;
            this.qty = qty;
            this.lineTotal = lineTotal;
        }

        // Getters and Setters
        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrderLine orderLine = (OrderLine) o;
            return Objects.equals(sku, orderLine.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku);
        }

        @Override
        public String toString() {
            return "OrderLine{" +
                    "sku='" + sku + '\'' +
                    ", name='" + name + '\'' +
                    ", unitPrice=" + unitPrice +
                    ", qty=" + qty +
                    ", lineTotal=" + lineTotal +
                    '}';
        }
    }

    /**
     * Order totals information.
     */
    public static class OrderTotals {
        @JsonProperty("items")
        @NotNull(message = "Items count is required")
        @Min(value = 0, message = "Items count must be non-negative")
        private Integer items;

        @JsonProperty("grand")
        @NotNull(message = "Grand total is required")
        @Min(value = 0, message = "Grand total must be non-negative")
        private BigDecimal grand;

        // Default constructor
        public OrderTotals() {}

        // Constructor with all fields
        public OrderTotals(Integer items, BigDecimal grand) {
            this.items = items;
            this.grand = grand;
        }

        // Getters and Setters
        public Integer getItems() {
            return items;
        }

        public void setItems(Integer items) {
            this.items = items;
        }

        public BigDecimal getGrand() {
            return grand;
        }

        public void setGrand(BigDecimal grand) {
            this.grand = grand;
        }

        @Override
        public String toString() {
            return "OrderTotals{" +
                    "items=" + items +
                    ", grand=" + grand +
                    '}';
        }
    }

    /**
     * Guest contact information for the order.
     */
    public static class GuestContact {
        @JsonProperty("name")
        @NotBlank(message = "Name is required")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        @NotNull(message = "Address is required")
        @Valid
        private Address address;

        // Default constructor
        public GuestContact() {}

        // Constructor with all fields
        public GuestContact(String name, String email, String phone, Address address) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.address = address;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return "GuestContact{" +
                    "name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", phone='" + phone + '\'' +
                    ", address=" + address +
                    '}';
        }
    }

    /**
     * Address information.
     */
    public static class Address {
        @JsonProperty("line1")
        @NotBlank(message = "Address line 1 is required")
        private String line1;

        @JsonProperty("city")
        @NotBlank(message = "City is required")
        private String city;

        @JsonProperty("postcode")
        @NotBlank(message = "Postcode is required")
        private String postcode;

        @JsonProperty("country")
        @NotBlank(message = "Country is required")
        private String country;

        // Default constructor
        public Address() {}

        // Constructor with all fields
        public Address(String line1, String city, String postcode, String country) {
            this.line1 = line1;
            this.city = city;
            this.postcode = postcode;
            this.country = country;
        }

        // Getters and Setters
        public String getLine1() {
            return line1;
        }

        public void setLine1(String line1) {
            this.line1 = line1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getPostcode() {
            return postcode;
        }

        public void setPostcode(String postcode) {
            this.postcode = postcode;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        @Override
        public String toString() {
            return "Address{" +
                    "line1='" + line1 + '\'' +
                    ", city='" + city + '\'' +
                    ", postcode='" + postcode + '\'' +
                    ", country='" + country + '\'' +
                    '}';
        }
    }
}
