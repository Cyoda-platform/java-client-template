package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HN Item Entity - Represents individual Hacker News items from the Firebase HN API
 * 
 * This entity supports all types of HN content including stories, comments, jobs, Ask HNs, and polls.
 * It follows the Firebase HN API JSON structure to maintain compatibility.
 */
@Data
public class HnItem implements CyodaEntity {
    public static final String ENTITY_NAME = HnItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required Fields
    /**
     * The item's unique identifier from Hacker News. This is the primary business identifier.
     */
    private Long id;
    
    /**
     * The type of item. One of "job", "story", "comment", "poll", or "pollopt".
     */
    private String type;

    // Optional Fields Based on Firebase HN API
    /**
     * true if the item is deleted.
     */
    private Boolean deleted;
    
    /**
     * The username of the item's author.
     */
    private String by;
    
    /**
     * Creation date of the item, in Unix Time.
     */
    private Long time;
    
    /**
     * The comment, story or poll text. HTML format.
     */
    private String text;
    
    /**
     * true if the item is dead.
     */
    private Boolean dead;
    
    /**
     * The comment's parent: either another comment or the relevant story.
     */
    private Long parent;
    
    /**
     * The pollopt's associated poll.
     */
    private Long poll;
    
    /**
     * The ids of the item's comments, in ranked display order.
     */
    private List<Long> kids;
    
    /**
     * The URL of the story.
     */
    private String url;
    
    /**
     * The story's score, or the votes for a pollopt.
     */
    private Integer score;
    
    /**
     * The title of the story, poll or job. HTML format.
     */
    private String title;
    
    /**
     * A list of related pollopts, in display order.
     */
    private List<Long> parts;
    
    /**
     * In the case of stories or polls, the total comment count.
     */
    private Integer descendants;

    // System Fields
    /**
     * When the entity was created in our system.
     */
    private LocalDateTime createdAt;
    
    /**
     * When the entity was last updated in our system.
     */
    private LocalDateTime updatedAt;
    
    /**
     * The Firebase API URL where this item was retrieved from.
     */
    private String sourceUrl;

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
        
        if (type == null || (!type.equals("job") && !type.equals("story") && 
                            !type.equals("comment") && !type.equals("poll") && 
                            !type.equals("pollopt"))) {
            return false;
        }
        
        // Validate type-specific rules
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
}
