package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Product entity representing the complete product catalog schema
 * with attributes, localizations, media, options, variants, bundles, inventory, compliance, and relationships.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String sku; // Business ID - required, unique
    private String name; // required
    private String description; // required
    private Double price; // required: default/base price (fallback)
    private Integer quantityAvailable; // required: quick projection field
    private String category; // required
    private String warehouseId; // optional default primary node

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
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               price != null && price >= 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    @Data
    public static class Attributes {
        private String brand;
        private String model;
        private Dimensions dimensions;
        private Weight weight;
        private List<Hazard> hazards;
        private Object custom; // open extension bag for teams
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
        private String clazz; // "class" is reserved keyword
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
        private Regulatory regulatory;
        private List<String> salesRestrictions;
    }

    @Data
    public static class Regulatory {
        private Boolean ukca;
        private Boolean ce;
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
        private List<OptionAxis> axes;
        private List<OptionConstraint> constraints;
    }

    @Data
    public static class OptionAxis {
        private String code;
        private List<String> values;
    }

    @Data
    public static class OptionConstraint {
        private Object ifCondition; // "if" is reserved keyword
        private Object then;
        private List<Object> requires;
        private List<String> whenRegionIn;
    }

    @Data
    public static class Variant {
        private String variantSku;
        private Object optionValues;
        private Attributes attributes; // overrides
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
        private String type; // "kit" (shipped together) or "bundle" (virtual)
        private String sku;
        private List<BundleComponent> components;
    }

    @Data
    public static class BundleComponent {
        private ComponentRef ref;
        private Integer qty;
        private Boolean optional;
        private Boolean defaultSelected;
        private List<ComponentConstraint> constraints;
        private List<ComponentSubstitution> substitutions;
    }

    @Data
    public static class ComponentRef {
        private String sku;
    }

    @Data
    public static class ComponentConstraint {
        private Object ifVariant;
        private Object then;
    }

    @Data
    public static class ComponentSubstitution {
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
        private NodeCapacity capacity;
        private List<InventoryLot> lots;
        private List<InventoryReservation> reservations;
        private List<InventoryInTransit> inTransit;
        private Integer qtyOnHand;
    }

    @Data
    public static class NodeCapacity {
        private Integer maxUnits;
    }

    @Data
    public static class InventoryLot {
        private String lotId;
        private String mfgDate;
        private String expires;
        private Integer qty;
        private List<String> serials;
        private String quality;
        private String reason;
    }

    @Data
    public static class InventoryReservation {
        private String ref;
        private String variantSku;
        private Integer qty;
        private String until;
    }

    @Data
    public static class InventoryInTransit {
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
        private List<ComplianceRestriction> restrictions;
    }

    @Data
    public static class ComplianceDoc {
        private String id;
        private List<String> regions;
        private String url;
        private Boolean approved;
    }

    @Data
    public static class ComplianceRestriction {
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
        private SupplierContract contract;
    }

    @Data
    public static class SupplierContract {
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
        private String at;
        private Object payload;
    }
}
