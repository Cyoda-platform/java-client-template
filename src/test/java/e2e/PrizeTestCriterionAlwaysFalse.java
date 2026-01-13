package e2e;

import java.util.concurrent.atomic.AtomicBoolean;

import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;

@Component
public class PrizeTestCriterionAlwaysFalse implements CyodaCriterion {

    private static final Logger LOG = LoggerFactory.getLogger(PrizeTestCriterionAlwaysTrue.class);

    private final AtomicBoolean isCriterionTriggered = new AtomicBoolean(false);
    private final CriterionSerializer serializer;

    public PrizeTestCriterionAlwaysFalse(final SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    public boolean isCriterionTriggered() {
        return isCriterionTriggered.get();
    }

    @Override
    public EntityCriteriaCalculationResponse check(final CyodaEventContext<EntityCriteriaCalculationRequest> request) {
        LOG.info("'check' Triggered");
        isCriterionTriggered.set(true);
        return serializer.responseBuilder(request.getEvent()).withNonMatch().build();
    }

    @Override
    public boolean supports(final OperationSpecification opsSpec) {
        LOG.info("'supports' Triggered");
        return "test-criterion-false".equals(opsSpec.getOperationName());
    }

}