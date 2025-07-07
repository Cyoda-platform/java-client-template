package com.java_template.common.workflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.WorkflowEntity;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pet entity for workflow processing.
 */
@Getter
public class PetEntity implements WorkflowEntity {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Getters and setters
    private Long id;
    private String name;
    private String status;
    private String category;
    private List<String> tags = new ArrayList<>();
    private List<String> photoUrls = new ArrayList<>();
    private String lastModified;
    
    public PetEntity() {}
    
    public PetEntity(ObjectNode objectNode) {
        fromObjectNode(objectNode);
    }
    
    @Override
    public ObjectNode toObjectNode() {
        ObjectNode node = MAPPER.createObjectNode();
        
        if (id != null) node.put("id", id);
        if (name != null) node.put("name", name);
        if (status != null) node.put("status", status);
        if (category != null) node.put("category", category);
        if (lastModified != null) node.put("lastModified", lastModified);
        
        if (!tags.isEmpty()) {
            ArrayNode tagsArray = MAPPER.createArrayNode();
            tags.forEach(tagsArray::add);
            node.set("tags", tagsArray);
        }
        
        if (!photoUrls.isEmpty()) {
            ArrayNode photosArray = MAPPER.createArrayNode();
            photoUrls.forEach(photosArray::add);
            node.set("photoUrls", photosArray);
        }
        
        return node;
    }
    
    @Override
    public void fromObjectNode(ObjectNode objectNode) {
        this.id = objectNode.path("id").isNumber() ? objectNode.path("id").asLong() : null;
        this.name = objectNode.path("name").asText(null);
        this.status = objectNode.path("status").asText(null);
        this.category = objectNode.path("category").asText(null);
        this.lastModified = objectNode.path("lastModified").asText(null);
        
        // Parse tags
        this.tags.clear();
        JsonNode tagsNode = objectNode.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                if (tag.isTextual()) {
                    this.tags.add(tag.asText());
                }
            }
        }
        
        // Parse photoUrls
        this.photoUrls.clear();
        JsonNode photosNode = objectNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photo : photosNode) {
                if (photo.isTextual()) {
                    this.photoUrls.add(photo.asText());
                }
            }
        }
    }
    
    @Override
    public String getEntityType() {
        return "pet";
    }
    
    @Override
    public boolean isValid() {
        return id != null && name != null && !name.trim().isEmpty();
    }
    
    // Business logic methods
    public void normalizeStatus() {
        if (status != null) {
            this.status = status.toLowerCase();
        }
    }
    
    public void addLastModifiedTimestamp() {
        this.lastModified = Instant.now().toString();
    }
    
    public boolean hasStatus() {
        return status != null && !status.trim().isEmpty();
    }

    public void setId(Long id) { this.id = id; }

    public void setName(String name) { this.name = name; }

    public void setStatus(String status) { this.status = status; }

    public void setCategory(String category) { this.category = category; }

    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls != null ? photoUrls : new ArrayList<>(); }

    public void setLastModified(String lastModified) { this.lastModified = lastModified; }
}
