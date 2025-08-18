package com.java_template.application.entity.hackernewsitem.version_1;

import com.fasterxml.jackson.databind.JsonNode;

public class HackerNewsItem {
    private String technicalId;
    private Long id;
    private String type;
    private JsonNode originalJson;
    private String importTimestamp;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String sourceJobTechnicalId;

    public String getTechnicalId() {
        return technicalId;
    }

    public void setTechnicalId(String technicalId) {
        this.technicalId = technicalId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonNode getOriginalJson() {
        return originalJson;
    }

    public void setOriginalJson(JsonNode originalJson) {
        this.originalJson = originalJson;
    }

    public String getImportTimestamp() {
        return importTimestamp;
    }

    public void setImportTimestamp(String importTimestamp) {
        this.importTimestamp = importTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSourceJobTechnicalId() {
        return sourceJobTechnicalId;
    }

    public void setSourceJobTechnicalId(String sourceJobTechnicalId) {
        this.sourceJobTechnicalId = sourceJobTechnicalId;
    }

    public boolean isValid() {
        return technicalId != null && originalJson != null;
    }
}
