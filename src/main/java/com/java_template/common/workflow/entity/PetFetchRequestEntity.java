package com.java_template.common.workflow.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.WorkflowEntity;

/**
 * Pet fetch request entity for workflow processing.
 */
public class PetFetchRequestEntity implements WorkflowEntity {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private String sourceUrl;
    private String status;
    private Boolean valid;
    private String requestId;
    private String createdAt;
    
    public PetFetchRequestEntity() {}
    
    public PetFetchRequestEntity(ObjectNode objectNode) {
        fromObjectNode(objectNode);
    }
    
    @Override
    public ObjectNode toObjectNode() {
        ObjectNode node = MAPPER.createObjectNode();
        
        if (sourceUrl != null) node.put("sourceUrl", sourceUrl);
        if (status != null) node.put("status", status);
        if (valid != null) node.put("valid", valid);
        if (requestId != null) node.put("requestId", requestId);
        if (createdAt != null) node.put("createdAt", createdAt);
        
        return node;
    }
    
    @Override
    public void fromObjectNode(ObjectNode objectNode) {
        this.sourceUrl = objectNode.path("sourceUrl").asText(null);
        this.status = objectNode.path("status").asText(null);
        this.valid = objectNode.path("valid").isBoolean() ? objectNode.path("valid").asBoolean() : null;
        this.requestId = objectNode.path("requestId").asText(null);
        this.createdAt = objectNode.path("createdAt").asText(null);
    }
    
    @Override
    public String getEntityType() {
        return "petfetchrequest";
    }
    
    @Override
    public boolean isValid() {
        return sourceUrl != null && !sourceUrl.trim().isEmpty() 
            && status != null && !status.trim().isEmpty();
    }
    
    // Business logic methods
    public void validateRequest() {
        this.valid = isValid();
    }
    
    public boolean isFetchRequestValid() {
        return isValid();
    }
    
    // Getters and setters
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }
    
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
