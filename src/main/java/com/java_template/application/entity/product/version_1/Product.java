package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product entity implementing the complete OMS product schema
 * Includes all required fields and complex nested structures for inventory management
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku;                    // required, unique business identifier
    private String name;                   // required
    private String description;            // required
    private Double price;                  // required: default/base price (fallback)
    private Integer quantityAvailable;     // required: quick projection field
    private String category;               // required
    
    // Optional core fields
    private String warehouseId;            // optional default primary node

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
    public boolean isValid() {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               price != null && price >= 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    // Nested classes for complex structures

    @Data
    public static class Attributes {
        private String brand;
        private String model;
        private Dimensions dimensions;
        private Weight weight;
        private List<Hazard> hazards;
        private Map<String, Object> custom;  // open extension bag
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
        private String clazz;  // 'class' is reserved keyword
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
        private String title;
        private List<String> tags;
        private List<String> regionScope;
        private String sha256;
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
        private Map<String, String> ifCondition;  // 'if' is reserved keyword
        private Map<String, Object> then;
        private List<Map<String, Object>> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class Variant {
        private String variantSku;
        private Map<String, String> optionValues;
        private Attributes attributes;  // overrides
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
        private String type;  // "kit" or "bundle"
        private String sku;
        private List<Component> components;
    }

    @Data
    public static class Component {
        private Reference ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ComponentConstraint> constraints;
        private List<Substitution> substitutions;
    }

    @Data
    public static class Reference {
        private String sku;
    }

    @Data
    public static class ComponentConstraint {
        private Map<String, Object> ifVariant;
        private Map<String, Object> then;
    }

    @Data
    public static class Substitution {
        private String sku;
        private List<String> whenRegionIn;
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
        private String mfgDate;
        private String expires;
        private Integer qty;
        private List<String> serials;
        private String quality;
        private String reason;
    }

    @Data
    public static class Reservation {
        private String ref;
        private String variantSku;
        private Integer qty;
        private String until;
    }

    @Data
    public static class InTransit {
        private String po;
        private String eta;
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
