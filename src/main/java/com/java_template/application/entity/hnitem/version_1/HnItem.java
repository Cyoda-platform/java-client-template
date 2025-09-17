package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * HnItem Entity - Represents a Hacker News item from Firebase HN API
 * 
 * This entity represents items from the Firebase HN API including:
 * - Stories, comments, jobs, polls, and poll options
 * - Hierarchical relationships through parent-child structure
 * - Poll-option relationships
 * - All fields from the Firebase HN API JSON format
 */
@Data
public class HnItem implements CyodaEntity {
    public static final String ENTITY_NAME = HnItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private Long id;           // Unique item identifier from HN
    private String type;       // Item type: "story", "comment", "job", "poll", or "pollopt"
    
    // Optional fields from Firebase HN API
    private String by;         // Username of the item's author
    private Long time;         // Creation date in Unix timestamp
    private String text;       // Comment, story or poll text (HTML format)
    private String url;        // URL of the story
    private String title;      // Title of story, poll or job (HTML format)
    private Integer score;     // Story score or poll option votes
    private Integer descendants; // Total comment count for stories/polls
    private Long parent;       // Parent item ID for comments
    private List<Long> kids;   // Child comment IDs in ranked order
    private List<Long> parts;  // Related poll options for polls
    private Long poll;         // Associated poll ID for poll options
    private Boolean deleted;   // True if item is deleted
    private Boolean dead;      // True if item is dead

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        if (id == null || id <= 0) {
            return false;
        }
        
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        
        // Validate type is one of the allowed values
        if (!isValidType(type)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates if the type is one of the allowed HN item types
     */
    private boolean isValidType(String type) {
        return "story".equals(type) || 
               "comment".equals(type) || 
               "job".equals(type) || 
               "poll".equals(type) || 
               "pollopt".equals(type);
    }
}
