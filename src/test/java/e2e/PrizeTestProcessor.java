package e2e;

import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PrizeTestProcessor implements CyodaProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(PrizeTestProcessor.class);

    private final AtomicBoolean isProcessTriggered = new AtomicBoolean(false);

    public boolean isProcessTriggered() {
        return isProcessTriggered.get();
    }

    @Override
    public EntityProcessorCalculationResponse process(final CyodaEventContext<EntityProcessorCalculationRequest> context) {
        LOG.info("'process' triggered");
        isProcessTriggered.set(true);
        final var resp = new EntityProcessorCalculationResponse();
        resp.setId(UUID.randomUUID().toString());
        resp.setEntityId(context.getEvent().getEntityId());
        resp.setRequestId(context.getEvent().getRequestId());
        return resp;
    }

    @Override
    public boolean supports(final OperationSpecification opSpec) {
        LOG.info("'supports' triggered");
        return true;
    }
}
