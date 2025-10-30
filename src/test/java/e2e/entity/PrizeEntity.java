package e2e.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.EntityMetadata;

public class PrizeEntity implements CyodaEntity {

    private static final String MODEL_NAME = "nobel-prize";
    private static final Integer MODEL_VERSION = 1;

    public String year;
    public String category;
    public String comment;

    public PrizeEntity() {
    }

    public PrizeEntity(final String year, final String category, final String comment) {
        this.year = year;
        this.category = category;
        this.comment = comment;
    }

    @Override
    public OperationSpecification getModelKey() {
        final var modelSpec = new org.cyoda.cloud.api.event.common.ModelSpec();
        modelSpec.setName(MODEL_NAME);
        modelSpec.setVersion(MODEL_VERSION);
        return new OperationSpecification.Entity(modelSpec, MODEL_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return CyodaEntity.super.isValid(metadata);
    }
}
