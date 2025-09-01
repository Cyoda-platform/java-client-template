package com.java_template.application.entity.cart.version_1;

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
 * Cart entity representing a shopping cart for anonymous users during the shopping process.
 */
public class Cart implements CyodaEntity {

    @JsonProperty("cartId")
    @NotBlank(message = "Cart ID is required")
    private String cartId;

    @JsonProperty("lines")
    @NotNull(message = "Lines are required")
    @Valid
    private List<CartLine> lines;

    @JsonProperty("totalItems")
    @NotNull(message = "Total items is required")
    @Min(value = 0, message = "Total items must be non-negative")
    private Integer totalItems;

    @JsonProperty("grandTotal")
    @NotNull(message = "Grand total is required")
    @Min(value = 0, message = "Grand total must be non-negative")
    private BigDecimal grandTotal;

    @JsonProperty("guestContact")
    @Valid
    private GuestContact guestContact;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Cart() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor with required fields
    public Cart(String cartId, List<CartLine> lines, Integer totalItems, BigDecimal grandTotal) {
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
               grandTotal != null && grandTotal.compareTo(BigDecimal.ZERO) >= 0;
    }

    // Getters and Setters
    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public List<CartLine> getLines() {
        return lines;
    }

    public void setLines(List<CartLine> lines) {
        this.lines = lines;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
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
        Cart cart = (Cart) o;
        return Objects.equals(cartId, cart.cartId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cartId);
    }

    @Override
    public String toString() {
        return "Cart{" +
                "cartId='" + cartId + '\'' +
                ", totalItems=" + totalItems +
                ", grandTotal=" + grandTotal +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Cart line item representing a product in the cart.
     */
    public static class CartLine {
        @JsonProperty("sku")
        @NotBlank(message = "SKU is required")
        private String sku;

        @JsonProperty("name")
        @NotBlank(message = "Product name is required")
        private String name;

        @JsonProperty("price")
        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be non-negative")
        private BigDecimal price;

        @JsonProperty("qty")
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer qty;

        // Default constructor
        public CartLine() {}

        // Constructor with all fields
        public CartLine(String sku, String name, BigDecimal price, Integer qty) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.qty = qty;
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

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }

        public BigDecimal getLineTotal() {
            if (price != null && qty != null) {
                return price.multiply(BigDecimal.valueOf(qty));
            }
            return BigDecimal.ZERO;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CartLine cartLine = (CartLine) o;
            return Objects.equals(sku, cartLine.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku);
        }

        @Override
        public String toString() {
            return "CartLine{" +
                    "sku='" + sku + '\'' +
                    ", name='" + name + '\'' +
                    ", price=" + price +
                    ", qty=" + qty +
                    '}';
        }
    }

    /**
     * Guest contact information for the cart.
     */
    public static class GuestContact {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        @Valid
        private Address address;

        // Default constructor
        public GuestContact() {}

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
        private String line1;

        @JsonProperty("city")
        private String city;

        @JsonProperty("postcode")
        private String postcode;

        @JsonProperty("country")
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
