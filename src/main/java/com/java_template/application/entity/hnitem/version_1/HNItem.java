package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * HNItem Entity - Represents a Hacker News item in Firebase HN API format
 * 
 * This entity supports all types of HN items: stories, comments, jobs, polls, and poll options.
 * It follows the exact structure of the Firebase HN API for compatibility.
 */
@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = HNItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private Long id;                    // The item's unique id
    private String type;                // Type: "job", "story", "comment", "poll", or "pollopt"
    
    // Optional core fields
    private Boolean deleted;            // true if the item is deleted
    private String by;                  // The username of the item's author
    private Long time;                  // Creation date in Unix Time
    private String text;                // Comment, story or poll text (HTML format)
    private Boolean dead;               // true if the item is dead
    private Long parent;                // Comment's parent: another comment or story
    private Long poll;                  // The pollopt's associated poll
    private List<Long> kids;            // IDs of item's comments, in ranked display order
    private String url;                 // URL of the story
    private Integer score;              // Story's score, or votes for a pollopt
    private String title;               // Title of story, poll or job (HTML format)
    private List<Long> parts;           // List of related pollopts, in display order
    private Integer descendants;        // Total comment count for stories or polls
    
    // Derived fields (computed by processors)
    private Integer directChildrenCount;
    private String domain;
    private Boolean urlValid;
    private Integer textLength;
    private Integer wordCount;
    private Boolean available;
    
    // Processing timestamps
    private Long validatedAt;
    private Long enrichedAt;
    private Long indexedAt;
    private Long processedAt;

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
        
        if (type == null || !isValidType(type)) {
            return false;
        }
        
        // Type-specific validations
        if ("comment".equals(type) && parent == null) {
            return false;
        }
        
        if ("pollopt".equals(type) && poll == null) {
            return false;
        }
        
        if ("poll".equals(type) && (parts == null || parts.isEmpty())) {
            return false;
        }
        
        return true;
    }
    
    private boolean isValidType(String type) {
        return "job".equals(type) || "story".equals(type) || "comment".equals(type) || 
               "poll".equals(type) || "pollopt".equals(type);
    }
}
