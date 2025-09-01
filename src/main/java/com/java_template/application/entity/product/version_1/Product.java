package com.java_template.application.entity.product.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Product entity representing a product in the catalog with full schema for persistence and round-trip.
 */
public class Product implements CyodaEntity {

    @JsonProperty("sku")
    @NotBlank(message = "SKU is required")
    private String sku;

    @JsonProperty("name")
    @NotBlank(message = "Product name is required")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("price")
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @JsonProperty("quantityAvailable")
    @NotNull(message = "Quantity available is required")
    @Min(value = 0, message = "Quantity available must be non-negative")
    private Integer quantityAvailable;

    @JsonProperty("category")
    @NotBlank(message = "Category is required")
    private String category;

    @JsonProperty("warehouseId")
    private String warehouseId;

    @JsonProperty("attributes")
    private Map<String, Object> attributes;

    @JsonProperty("localizations")
    private Map<String, Object> localizations;

    @JsonProperty("media")
    private Map<String, Object> media;

    @JsonProperty("options")
    private Map<String, Object> options;

    @JsonProperty("variants")
    private Map<String, Object> variants;

    @JsonProperty("bundles")
    private Map<String, Object> bundles;

    @JsonProperty("inventory")
    private Map<String, Object> inventory;

    @JsonProperty("compliance")
    private Map<String, Object> compliance;

    @JsonProperty("relationships")
    private Map<String, Object> relationships;

    @JsonProperty("events")
    private Map<String, Object> events;

    // Default constructor
    public Product() {}

    // Constructor with required fields
    public Product(String sku, String name, BigDecimal price, Integer quantityAvailable, String category) {
        this.sku = sku;
        this.name = name;
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
               price != null && price.compareTo(BigDecimal.ZERO) > 0 &&
               quantityAvailable != null && quantityAvailable >= 0 &&
               category != null && !category.trim().isEmpty();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantityAvailable() {
        return quantityAvailable;
    }

    public void setQuantityAvailable(Integer quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> getLocalizations() {
        return localizations;
    }

    public void setLocalizations(Map<String, Object> localizations) {
        this.localizations = localizations;
    }

    public Map<String, Object> getMedia() {
        return media;
    }

    public void setMedia(Map<String, Object> media) {
        this.media = media;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public Map<String, Object> getVariants() {
        return variants;
    }

    public void setVariants(Map<String, Object> variants) {
        this.variants = variants;
    }

    public Map<String, Object> getBundles() {
        return bundles;
    }

    public void setBundles(Map<String, Object> bundles) {
        this.bundles = bundles;
    }

    public Map<String, Object> getInventory() {
        return inventory;
    }

    public void setInventory(Map<String, Object> inventory) {
        this.inventory = inventory;
    }

    public Map<String, Object> getCompliance() {
        return compliance;
    }

    public void setCompliance(Map<String, Object> compliance) {
        this.compliance = compliance;
    }

    public Map<String, Object> getRelationships() {
        return relationships;
    }

    public void setRelationships(Map<String, Object> relationships) {
        this.relationships = relationships;
    }

    public Map<String, Object> getEvents() {
        return events;
    }

    public void setEvents(Map<String, Object> events) {
        this.events = events;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(sku, product.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku);
    }

    @Override
    public String toString() {
        return "Product{" +
                "sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", quantityAvailable=" + quantityAvailable +
                ", category='" + category + '\'' +
                '}';
    }
}
