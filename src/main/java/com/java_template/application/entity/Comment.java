package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class Comment implements CyodaEntity {
    private String id; // business ID (comment ID from external source)
    private UUID technicalId; // database ID
    private Long postId; // post ID the comment belongs to
    private String name; // commenter's name
    private String email; // commenter's email
    private String body; // comment text
    private String ingestionJobId; // reference to CommentIngestionJob (UUID as String)
    private String status; // StatusEnum (RAW, ANALYZED)

    public Comment() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("comment");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "comment");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && postId != null
            && name != null && !name.isBlank()
            && email != null && !email.isBlank()
            && body != null && !body.isBlank()
            && ingestionJobId != null && !ingestionJobId.isBlank();
    }
}
