package e2e;

import java.util.concurrent.atomic.AtomicBoolean;

import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;

@Component
public class PrizeTestCriterionAlwaysTrue implements CyodaCriterion {

    private static final Logger LOG = LoggerFactory.getLogger(PrizeTestCriterionAlwaysTrue.class);

    private final AtomicBoolean isCriterionTriggered = new AtomicBoolean(false);
    private final CriterionSerializer serializer;

    public PrizeTestCriterionAlwaysTrue(final SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    public boolean isCriterionTriggered() {
        return isCriterionTriggered.get();
    }

    @Override
    public EntityCriteriaCalculationResponse check(final CyodaEventContext<EntityCriteriaCalculationRequest> request) {
        LOG.info("'check' Triggered");
        isCriterionTriggered.set(true);
        return serializer.withRequest(request.getEvent())
                .evaluate(jsonNode -> EvaluationOutcome.success())
                .complete();
    }

    @Override
    public boolean supports(final OperationSpecification opsSpec) {
        LOG.info("'supports' Triggered");
        System.out.println();
        return "test-criterion-true".equals(opsSpec.getOperationName());
    }

}