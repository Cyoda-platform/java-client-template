package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

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
    private Instant createdAt;

    @JsonProperty("updatedAt")
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
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    @Data
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
    }

    @Data
    public static class OrderTotals {
        @JsonProperty("items")
        private Double items;

        @JsonProperty("grand")
        private Double grand;
    }

    @Data
    public static class GuestContact {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        private Address address;

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    @Data
    public static class Address {
        @JsonProperty("line1")
        private String line1;

        @JsonProperty("city")
        private String city;

        @JsonProperty("postcode")
        private String postcode;

        @JsonProperty("country")
        private String country;

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
