package com.java_template.application.entity.product.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product entity representing a product in the e-commerce catalog with full schema support.
 */
public class Product implements CyodaEntity {

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

    @JsonProperty("warehouseId")
    private String warehouseId;

    // Complex attributes
    @JsonProperty("attributes")
    private ProductAttributes attributes;

    @JsonProperty("localizations")
    private Map<String, ProductLocalization> localizations;

    @JsonProperty("media")
    private List<ProductMedia> media;

    @JsonProperty("options")
    private ProductOptions options;

    @JsonProperty("variants")
    private List<ProductVariant> variants;

    @JsonProperty("bundles")
    private List<ProductBundle> bundles;

    @JsonProperty("inventory")
    private ProductInventory inventory;

    @JsonProperty("compliance")
    private ProductCompliance compliance;

    @JsonProperty("relationships")
    private ProductRelationships relationships;

    @JsonProperty("events")
    private List<ProductEvent> events;

    // Default constructor
    public Product() {}

    // Constructor with required fields
    public Product(String sku, String name, String description, Double price, Integer quantityAvailable, String category) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantityAvailable = quantityAvailable;
        this.category = category;
    }

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Product");
        modelSpec.setVersion(1);
        return new OperationSpecification.Entity(modelSpec, "Product");
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return sku != null && !sku.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               price != null && price > 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
    }

    // Getters and setters
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(Integer quantityAvailable) { this.quantityAvailable = quantityAvailable; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getWarehouseId() { return warehouseId; }
    public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }

    public ProductAttributes getAttributes() { return attributes; }
    public void setAttributes(ProductAttributes attributes) { this.attributes = attributes; }

    public Map<String, ProductLocalization> getLocalizations() { return localizations; }
    public void setLocalizations(Map<String, ProductLocalization> localizations) { this.localizations = localizations; }

    public List<ProductMedia> getMedia() { return media; }
    public void setMedia(List<ProductMedia> media) { this.media = media; }

    public ProductOptions getOptions() { return options; }
    public void setOptions(ProductOptions options) { this.options = options; }

    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }

    public List<ProductBundle> getBundles() { return bundles; }
    public void setBundles(List<ProductBundle> bundles) { this.bundles = bundles; }

    public ProductInventory getInventory() { return inventory; }
    public void setInventory(ProductInventory inventory) { this.inventory = inventory; }

    public ProductCompliance getCompliance() { return compliance; }
    public void setCompliance(ProductCompliance compliance) { this.compliance = compliance; }

    public ProductRelationships getRelationships() { return relationships; }
    public void setRelationships(ProductRelationships relationships) { this.relationships = relationships; }

    public List<ProductEvent> getEvents() { return events; }
    public void setEvents(List<ProductEvent> events) { this.events = events; }

    // Inner classes for complex attributes
    public static class ProductAttributes {
        @JsonProperty("brand")
        private String brand;

        @JsonProperty("model")
        private String model;

        @JsonProperty("dimensions")
        private ProductDimensions dimensions;

        @JsonProperty("weight")
        private Double weight;

        @JsonProperty("hazards")
        private List<String> hazards;

        @JsonProperty("customFields")
        private Map<String, Object> customFields;

        // Constructors, getters and setters
        public ProductAttributes() {}

        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public ProductDimensions getDimensions() { return dimensions; }
        public void setDimensions(ProductDimensions dimensions) { this.dimensions = dimensions; }

        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }

        public List<String> getHazards() { return hazards; }
        public void setHazards(List<String> hazards) { this.hazards = hazards; }

        public Map<String, Object> getCustomFields() { return customFields; }
        public void setCustomFields(Map<String, Object> customFields) { this.customFields = customFields; }
    }

    public static class ProductDimensions {
        @JsonProperty("length")
        private Double length;

        @JsonProperty("width")
        private Double width;

        @JsonProperty("height")
        private Double height;

        @JsonProperty("unit")
        private String unit;

        public ProductDimensions() {}

        public Double getLength() { return length; }
        public void setLength(Double length) { this.length = length; }

        public Double getWidth() { return width; }
        public void setWidth(Double width) { this.width = width; }

        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    public static class ProductLocalization {
        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("regulatoryInfo")
        private String regulatoryInfo;

        public ProductLocalization() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getRegulatoryInfo() { return regulatoryInfo; }
        public void setRegulatoryInfo(String regulatoryInfo) { this.regulatoryInfo = regulatoryInfo; }
    }

    public static class ProductMedia {
        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        public ProductMedia() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class ProductOptions {
        @JsonProperty("color")
        private List<String> color;

        @JsonProperty("capacity")
        private List<String> capacity;

        @JsonProperty("constraints")
        private Map<String, Object> constraints;

        public ProductOptions() {}

        public List<String> getColor() { return color; }
        public void setColor(List<String> color) { this.color = color; }

        public List<String> getCapacity() { return capacity; }
        public void setCapacity(List<String> capacity) { this.capacity = capacity; }

        public Map<String, Object> getConstraints() { return constraints; }
        public void setConstraints(Map<String, Object> constraints) { this.constraints = constraints; }
    }

    public static class ProductVariant {
        @JsonProperty("optionValues")
        private Map<String, String> optionValues;

        @JsonProperty("barcode")
        private String barcode;

        @JsonProperty("pricingOverrides")
        private Map<String, Double> pricingOverrides;

        public ProductVariant() {}

        public Map<String, String> getOptionValues() { return optionValues; }
        public void setOptionValues(Map<String, String> optionValues) { this.optionValues = optionValues; }

        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }

        public Map<String, Double> getPricingOverrides() { return pricingOverrides; }
        public void setPricingOverrides(Map<String, Double> pricingOverrides) { this.pricingOverrides = pricingOverrides; }
    }

    public static class ProductBundle {
        @JsonProperty("type")
        private String type;

        @JsonProperty("components")
        private List<BundleComponent> components;

        public ProductBundle() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<BundleComponent> getComponents() { return components; }
        public void setComponents(List<BundleComponent> components) { this.components = components; }

        public static class BundleComponent {
            @JsonProperty("sku")
            private String sku;

            @JsonProperty("quantity")
            private Integer quantity;

            public BundleComponent() {}

            public String getSku() { return sku; }
            public void setSku(String sku) { this.sku = sku; }

            public Integer getQuantity() { return quantity; }
            public void setQuantity(Integer quantity) { this.quantity = quantity; }
        }
    }

    public static class ProductInventory {
        @JsonProperty("nodes")
        private List<InventoryNode> nodes;

        @JsonProperty("lots")
        private List<InventoryLot> lots;

        @JsonProperty("reservations")
        private List<InventoryReservation> reservations;

        @JsonProperty("policies")
        private Map<String, Object> policies;

        public ProductInventory() {}

        public List<InventoryNode> getNodes() { return nodes; }
        public void setNodes(List<InventoryNode> nodes) { this.nodes = nodes; }

        public List<InventoryLot> getLots() { return lots; }
        public void setLots(List<InventoryLot> lots) { this.lots = lots; }

        public List<InventoryReservation> getReservations() { return reservations; }
        public void setReservations(List<InventoryReservation> reservations) { this.reservations = reservations; }

        public Map<String, Object> getPolicies() { return policies; }
        public void setPolicies(Map<String, Object> policies) { this.policies = policies; }

        public static class InventoryNode {
            @JsonProperty("nodeId")
            private String nodeId;

            @JsonProperty("quantity")
            private Integer quantity;

            public InventoryNode() {}

            public String getNodeId() { return nodeId; }
            public void setNodeId(String nodeId) { this.nodeId = nodeId; }

            public Integer getQuantity() { return quantity; }
            public void setQuantity(Integer quantity) { this.quantity = quantity; }
        }

        public static class InventoryLot {
            @JsonProperty("lotId")
            private String lotId;

            @JsonProperty("quantity")
            private Integer quantity;

            @JsonProperty("expiryDate")
            private LocalDateTime expiryDate;

            public InventoryLot() {}

            public String getLotId() { return lotId; }
            public void setLotId(String lotId) { this.lotId = lotId; }

            public Integer getQuantity() { return quantity; }
            public void setQuantity(Integer quantity) { this.quantity = quantity; }

            public LocalDateTime getExpiryDate() { return expiryDate; }
            public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
        }

        public static class InventoryReservation {
            @JsonProperty("reservationId")
            private String reservationId;

            @JsonProperty("quantity")
            private Integer quantity;

            @JsonProperty("expiryDate")
            private LocalDateTime expiryDate;

            public InventoryReservation() {}

            public String getReservationId() { return reservationId; }
            public void setReservationId(String reservationId) { this.reservationId = reservationId; }

            public Integer getQuantity() { return quantity; }
            public void setQuantity(Integer quantity) { this.quantity = quantity; }

            public LocalDateTime getExpiryDate() { return expiryDate; }
            public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
        }
    }

    public static class ProductCompliance {
        @JsonProperty("documents")
        private List<ComplianceDocument> documents;

        @JsonProperty("restrictions")
        private List<String> restrictions;

        public ProductCompliance() {}

        public List<ComplianceDocument> getDocuments() { return documents; }
        public void setDocuments(List<ComplianceDocument> documents) { this.documents = documents; }

        public List<String> getRestrictions() { return restrictions; }
        public void setRestrictions(List<String> restrictions) { this.restrictions = restrictions; }

        public static class ComplianceDocument {
            @JsonProperty("type")
            private String type;

            @JsonProperty("url")
            private String url;

            @JsonProperty("validUntil")
            private LocalDateTime validUntil;

            public ComplianceDocument() {}

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }

            public LocalDateTime getValidUntil() { return validUntil; }
            public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
        }
    }

    public static class ProductRelationships {
        @JsonProperty("suppliers")
        private List<SupplierInfo> suppliers;

        @JsonProperty("relatedProducts")
        private List<String> relatedProducts;

        public ProductRelationships() {}

        public List<SupplierInfo> getSuppliers() { return suppliers; }
        public void setSuppliers(List<SupplierInfo> suppliers) { this.suppliers = suppliers; }

        public List<String> getRelatedProducts() { return relatedProducts; }
        public void setRelatedProducts(List<String> relatedProducts) { this.relatedProducts = relatedProducts; }

        public static class SupplierInfo {
            @JsonProperty("supplierId")
            private String supplierId;

            @JsonProperty("supplierName")
            private String supplierName;

            @JsonProperty("supplierSku")
            private String supplierSku;

            public SupplierInfo() {}

            public String getSupplierId() { return supplierId; }
            public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

            public String getSupplierName() { return supplierName; }
            public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

            public String getSupplierSku() { return supplierSku; }
            public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
        }
    }

    public static class ProductEvent {
        @JsonProperty("eventType")
        private String eventType;

        @JsonProperty("timestamp")
        private LocalDateTime timestamp;

        @JsonProperty("data")
        private Map<String, Object> data;

        public ProductEvent() {}

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }
}
