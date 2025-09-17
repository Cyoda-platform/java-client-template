package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * HNItem Entity - Represents a Hacker News item following the Firebase HN API JSON format
 * Supports all item types: story, comment, job, poll, and pollopt
 */
@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = HNItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private Integer id;              // Unique integer identifier (required)
    private String type;             // Item type - "story", "comment", "job", "poll", or "pollopt" (required)
    
    // Optional fields for additional business data
    private String by;               // Username of the author
    private Long time;               // Creation timestamp (Unix time)
    private String title;            // Title for stories, polls, or jobs (HTML)
    private String text;             // Content text (HTML)
    private String url;              // URL for stories
    private Integer score;           // Score/votes for stories and pollopts
    private Integer parent;          // Parent item ID for comments
    private List<Integer> kids;      // Array of child comment IDs
    private Integer descendants;     // Total comment count for stories/polls
    private List<Integer> parts;     // Related pollopt IDs for polls
    private Integer poll;            // Associated poll ID for pollopts
    private Boolean deleted;         // Boolean flag for deleted items
    private Boolean dead;            // Boolean flag for dead items

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
        return id != null && type != null && 
               (type.equals("story") || type.equals("comment") || type.equals("job") || 
                type.equals("poll") || type.equals("pollopt"));
    }
}
