package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommentIngestionJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private Long postId; // the post ID to fetch comments for
    private String status; // StatusEnum (PENDING, PROCESSING, COMPLETED, FAILED)
    private LocalDateTime requestedAt; // timestamp when job was created
    private LocalDateTime completedAt; // timestamp when job finished
    private String reportEmail; // email address to send analysis report

    public CommentIngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("commentIngestionJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "commentIngestionJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && postId != null
            && reportEmail != null && !reportEmail.isBlank();
    }
}
