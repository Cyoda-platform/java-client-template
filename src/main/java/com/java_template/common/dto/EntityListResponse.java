package com.java_template.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.DataPayload;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper for collections of entities with their metadata.
 * Provides convenient access to both business data and technical metadata
 * for each entity in the collection.
 * 
 * @param <T> The type of the business entity
 */
public class EntityListResponse<T extends CyodaEntity> {
    
    @JsonProperty("items")
    private final List<EntityResponse<T>> items;
    
    @JsonProperty("totalCount")
    private final int totalCount;
    
    public EntityListResponse(List<EntityResponse<T>> items) {
        this.items = items;
        this.totalCount = items.size();
    }
    
    public List<EntityResponse<T>> getItems() {
        return items;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    // Convenience method to get just the business data (for backward compatibility)
    public List<T> getData() {
        return items.stream()
                .map(EntityResponse::getData)
                .collect(Collectors.toList());
    }
    
    // Factory method for creating from list of DataPayloads
    public static <T extends CyodaEntity> EntityListResponse<T> fromDataPayloads(
            List<DataPayload> payloads, 
            Class<T> entityClass,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        
        List<EntityResponse<T>> items = payloads.stream()
                .map(payload -> EntityResponse.fromDataPayload(payload, entityClass, objectMapper))
                .collect(Collectors.toList());
        
        return new EntityListResponse<>(items);
    }
    
    @Override
    public String toString() {
        return "EntityListResponse{" +
                "totalCount=" + totalCount +
                ", items=" + items +
                '}';
    }
}
