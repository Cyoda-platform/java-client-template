package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "PRODUCT";
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    @JsonProperty("sku")
    private String sku;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("quantityAvailable")
    private Integer quantityAvailable;

    @JsonProperty("category")
    private String category;

    // Optional fields
    @JsonProperty("warehouseId")
    private String warehouseId;

    @JsonProperty("attributes")
    private Attributes attributes;

    @JsonProperty("localizations")
    private Localizations localizations;

    @JsonProperty("media")
    private List<Media> media;

    @JsonProperty("options")
    private Options options;

    @JsonProperty("variants")
    private List<Variant> variants;

    @JsonProperty("bundles")
    private List<Bundle> bundles;

    @JsonProperty("inventory")
    private Inventory inventory;

    @JsonProperty("compliance")
    private Compliance compliance;

    @JsonProperty("relationships")
    private Relationships relationships;

    @JsonProperty("events")
    private List<Event> events;

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
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               price != null && price > 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    // Nested classes for complex attributes
    @Data
    public static class Attributes {
        @JsonProperty("brand")
        private String brand;

        @JsonProperty("model")
        private String model;

        @JsonProperty("dimensions")
        private Dimensions dimensions;

        @JsonProperty("weight")
        private Weight weight;

        @JsonProperty("hazards")
        private List<Hazard> hazards;

        @JsonProperty("custom")
        private Map<String, Object> custom;
    }

    @Data
    public static class Dimensions {
        @JsonProperty("l")
        private Double l;

        @JsonProperty("w")
        private Double w;

        @JsonProperty("h")
        private Double h;

        @JsonProperty("unit")
        private String unit;
    }

    @Data
    public static class Weight {
        @JsonProperty("value")
        private Double value;

        @JsonProperty("unit")
        private String unit;
    }

    @Data
    public static class Hazard {
        @JsonProperty("class")
        private String hazardClass;

        @JsonProperty("transportNotes")
        private String transportNotes;
    }

    @Data
    public static class Localizations {
        @JsonProperty("defaultLocale")
        private String defaultLocale;

        @JsonProperty("content")
        private List<LocalizationContent> content;
    }

    @Data
    public static class LocalizationContent {
        @JsonProperty("locale")
        private String locale;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("regulatory")
        private Map<String, Object> regulatory;

        @JsonProperty("salesRestrictions")
        private List<String> salesRestrictions;
    }

    @Data
    public static class Media {
        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("alt")
        private String alt;

        @JsonProperty("title")
        private String title;

        @JsonProperty("tags")
        private List<String> tags;

        @JsonProperty("sha256")
        private String sha256;

        @JsonProperty("regionScope")
        private List<String> regionScope;
    }

    @Data
    public static class Options {
        @JsonProperty("axes")
        private List<OptionAxis> axes;

        @JsonProperty("constraints")
        private List<OptionConstraint> constraints;
    }

    @Data
    public static class OptionAxis {
        @JsonProperty("code")
        private String code;

        @JsonProperty("values")
        private List<String> values;
    }

    @Data
    public static class OptionConstraint {
        @JsonProperty("if")
        private Map<String, Object> ifCondition;

        @JsonProperty("then")
        private Map<String, Object> thenCondition;

        @JsonProperty("requires")
        private List<Map<String, Object>> requires;

        @JsonProperty("whenRegionIn")
        private List<String> whenRegionIn;
    }

    @Data
    public static class Variant {
        @JsonProperty("variantSku")
        private String variantSku;

        @JsonProperty("optionValues")
        private Map<String, String> optionValues;

        @JsonProperty("attributes")
        private Map<String, Object> attributes;

        @JsonProperty("barcodes")
        private List<String> barcodes;

        @JsonProperty("priceOverrides")
        private PriceOverrides priceOverrides;

        @JsonProperty("inventoryPolicy")
        private Map<String, Object> inventoryPolicy;
    }

    @Data
    public static class PriceOverrides {
        @JsonProperty("base")
        private Double base;

        @JsonProperty("priceBooks")
        private List<String> priceBooks;
    }

    @Data
    public static class Bundle {
        @JsonProperty("type")
        private String type;

        @JsonProperty("sku")
        private String sku;

        @JsonProperty("components")
        private List<BundleComponent> components;
    }

    @Data
    public static class BundleComponent {
        @JsonProperty("ref")
        private Map<String, String> ref;

        @JsonProperty("qty")
        private Integer qty;

        @JsonProperty("optional")
        private Boolean optional;

        @JsonProperty("defaultSelected")
        private Boolean defaultSelected;

        @JsonProperty("constraints")
        private List<Map<String, Object>> constraints;

        @JsonProperty("substitutions")
        private List<Map<String, Object>> substitutions;
    }

    @Data
    public static class Inventory {
        @JsonProperty("nodes")
        private List<InventoryNode> nodes;

        @JsonProperty("policies")
        private Map<String, Object> policies;
    }

    @Data
    public static class InventoryNode {
        @JsonProperty("nodeId")
        private String nodeId;

        @JsonProperty("type")
        private String type;

        @JsonProperty("capacity")
        private Map<String, Object> capacity;

        @JsonProperty("lots")
        private List<InventoryLot> lots;

        @JsonProperty("reservations")
        private List<Map<String, Object>> reservations;

        @JsonProperty("inTransit")
        private List<Map<String, Object>> inTransit;

        @JsonProperty("qtyOnHand")
        private Integer qtyOnHand;
    }

    @Data
    public static class InventoryLot {
        @JsonProperty("lotId")
        private String lotId;

        @JsonProperty("mfgDate")
        private String mfgDate;

        @JsonProperty("expires")
        private String expires;

        @JsonProperty("qty")
        private Integer qty;

        @JsonProperty("serials")
        private List<String> serials;

        @JsonProperty("quality")
        private String quality;

        @JsonProperty("reason")
        private String reason;
    }

    @Data
    public static class Compliance {
        @JsonProperty("docs")
        private List<ComplianceDoc> docs;

        @JsonProperty("restrictions")
        private List<ComplianceRestriction> restrictions;
    }

    @Data
    public static class ComplianceDoc {
        @JsonProperty("id")
        private String id;

        @JsonProperty("regions")
        private List<String> regions;

        @JsonProperty("url")
        private String url;

        @JsonProperty("approved")
        private Boolean approved;
    }

    @Data
    public static class ComplianceRestriction {
        @JsonProperty("region")
        private String region;

        @JsonProperty("rules")
        private List<String> rules;

        @JsonProperty("reason")
        private String reason;
    }

    @Data
    public static class Relationships {
        @JsonProperty("suppliers")
        private List<Supplier> suppliers;

        @JsonProperty("relatedProducts")
        private List<RelatedProduct> relatedProducts;
    }

    @Data
    public static class Supplier {
        @JsonProperty("partyId")
        private String partyId;

        @JsonProperty("contract")
        private Map<String, Object> contract;
    }

    @Data
    public static class RelatedProduct {
        @JsonProperty("type")
        private String type;

        @JsonProperty("sku")
        private String sku;
    }

    @Data
    public static class Event {
        @JsonProperty("type")
        private String type;

        @JsonProperty("at")
        private String at;

        @JsonProperty("payload")
        private Map<String, Object> payload;
    }
}
