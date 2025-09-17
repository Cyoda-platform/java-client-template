package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HNItem Entity - Represents a Hacker News item from Firebase HN API
 * 
 * This entity represents stories, comments, jobs, Ask HNs, and polls from Hacker News.
 * It closely mirrors the Firebase Hacker News API structure with additional technical fields.
 */
@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = HNItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required Fields
    private Long id;                    // The item's unique id from Hacker News
    private String type;                // One of "job", "story", "comment", "poll", or "pollopt"

    // Optional Fields from Firebase HN API
    private Boolean deleted;            // true if the item is deleted
    private String by;                  // The username of the item's author
    private Long time;                  // Creation date of the item, in Unix Time
    private String text;                // The comment, story or poll text. HTML
    private Boolean dead;               // true if the item is dead
    private Long parent;                // The comment's parent: either another comment or the relevant story
    private Long poll;                  // The pollopt's associated poll
    private List<Long> kids;            // The ids of the item's comments, in ranked display order
    private String url;                 // The URL of the story
    private Integer score;              // The story's score, or the votes for a pollopt
    private String title;               // The title of the story, poll or job. HTML
    private List<Long> parts;           // A list of related pollopts, in display order
    private Integer descendants;        // In the case of stories or polls, the total comment count

    // Technical Fields
    private LocalDateTime createdAt;    // When the entity was created in our system
    private LocalDateTime updatedAt;    // When the entity was last updated in our system
    private String sourceType;         // How this item was added ("API_PULL", "SINGLE_POST", "ARRAY_POST", "BULK_UPLOAD")

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
        if (id == null) {
            return false;
        }
        
        if (type == null || !isValidType(type)) {
            return false;
        }
        
        // Business rule validations
        if ("comment".equals(type) && parent == null) {
            return false;
        }
        
        if ("poll".equals(type) && (parts == null || parts.isEmpty())) {
            return false;
        }
        
        if ("pollopt".equals(type) && poll == null) {
            return false;
        }
        
        return true;
    }

    /**
     * Validates if the type is one of the allowed HN item types
     */
    private boolean isValidType(String type) {
        return "job".equals(type) || 
               "story".equals(type) || 
               "comment".equals(type) || 
               "poll".equals(type) || 
               "pollopt".equals(type);
    }
}
