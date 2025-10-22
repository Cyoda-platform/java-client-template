package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: Product entity representing a catalog item with full schema including
 * attributes, localizations, media, options, variants, bundles, inventory, compliance, and relationships.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String sku;
    
    // Required core fields
    private String name;
    private String description;
    private Double price;
    private Integer quantityAvailable;
    private String category;
    
    // Optional fields
    private String warehouseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Complex nested structures
    private Attributes attributes;
    private Localizations localizations;
    private List<Media> media;
    private Options options;
    private List<Variant> variants;
    private List<Bundle> bundles;
    private Inventory inventory;
    private Compliance compliance;
    private Relationships relationships;
    private List<Event> events;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               price != null && quantityAvailable != null &&
               category != null && !category.trim().isEmpty();
    }

    @Data
    public static class Attributes {
        private String brand;
        private String model;
        private Dimensions dimensions;
        private Weight weight;
        private List<Hazard> hazards;
        private Map<String, Object> custom;
    }

    @Data
    public static class Dimensions {
        private Double l;
        private Double w;
        private Double h;
        private String unit;
    }

    @Data
    public static class Weight {
        private Double value;
        private String unit;
    }

    @Data
    public static class Hazard {
        private String clazz;
        private String transportNotes;
    }

    @Data
    public static class Localizations {
        private String defaultLocale;
        private List<LocalizationContent> content;
    }

    @Data
    public static class LocalizationContent {
        private String locale;
        private String name;
        private String description;
        private Map<String, Object> regulatory;
        private List<String> salesRestrictions;
    }

    @Data
    public static class Media {
        private String type;
        private String url;
        private String alt;
        private List<String> tags;
        private String sha256;
        private String title;
        private List<String> regionScope;
    }

    @Data
    public static class Options {
        private List<Axis> axes;
        private List<Constraint> constraints;
    }

    @Data
    public static class Axis {
        private String code;
        private List<String> values;
    }

    @Data
    public static class Constraint {
        private Map<String, Object> ifCondition;
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class Variant {
        private String variantSku;
        private Map<String, String> optionValues;
        private Map<String, Object> attributes;
        private List<String> barcodes;
        private PriceOverrides priceOverrides;
        private InventoryPolicy inventoryPolicy;
    }

    @Data
    public static class PriceOverrides {
        private Double base;
        private List<String> priceBooks;
    }

    @Data
    public static class InventoryPolicy {
        private Boolean backorder;
        private Integer maxBackorderDays;
    }

    @Data
    public static class Bundle {
        private String type;
        private String sku;
        private List<BundleComponent> components;
    }

    @Data
    public static class BundleComponent {
        private BundleRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<Map<String, Object>> constraints;
    }

    @Data
    public static class BundleRef {
        private String sku;
    }

    @Data
    public static class Inventory {
        private List<InventoryNode> nodes;
        private InventoryPolicies policies;
    }

    @Data
    public static class InventoryNode {
        private String nodeId;
        private String type;
        private Capacity capacity;
        private List<Lot> lots;
        private List<Reservation> reservations;
        private List<InTransit> inTransit;
        private Integer qtyOnHand;
    }

    @Data
    public static class Capacity {
        private Integer maxUnits;
    }

    @Data
    public static class Lot {
        private String lotId;
        private LocalDateTime mfgDate;
        private LocalDateTime expires;
        private Integer qty;
        private List<String> serials;
        private String quality;
    }

    @Data
    public static class Reservation {
        private String ref;
        private String variantSku;
        private Integer qty;
        private LocalDateTime until;
    }

    @Data
    public static class InTransit {
        private String po;
        private LocalDateTime eta;
        private Integer qty;
        private String status;
    }

    @Data
    public static class InventoryPolicies {
        private String allocation;
        private OversellGuard oversellGuard;
    }

    @Data
    public static class OversellGuard {
        private Integer maxPercent;
    }

    @Data
    public static class Compliance {
        private List<ComplianceDoc> docs;
        private List<Restriction> restrictions;
    }

    @Data
    public static class ComplianceDoc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

    @Data
    public static class Restriction {
        private String region;
        private List<String> rules;
        private String reason;
    }

    @Data
    public static class Relationships {
        private List<Supplier> suppliers;
        private List<RelatedProduct> relatedProducts;
    }

    @Data
    public static class Supplier {
        private String partyId;
        private Contract contract;
    }

    @Data
    public static class Contract {
        private String id;
        private String incoterm;
        private Integer leadTimeDays;
    }

    @Data
    public static class RelatedProduct {
        private String type;
        private String sku;
    }

    @Data
    public static class Event {
        private String type;
        private LocalDateTime at;
        private Map<String, Object> payload;
    }
}

