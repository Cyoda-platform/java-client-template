package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BackfillProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BackfillProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BackfillProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BackfillProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber for backfill")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber s) {
        return s != null && Boolean.TRUE.equals(s.getActive());
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        // In a real system we would page through laureates since backfillFromDate and enqueue notifications
        LocalDate from = null;
        try {
            if (subscriber.getBackfillFromDate() != null) {
                from = LocalDate.parse(subscriber.getBackfillFromDate());
            }
        } catch (Exception e) {
            logger.warn("Invalid backfillFromDate for subscriber {}: {}", subscriber.getTechnicalId(), subscriber.getBackfillFromDate());
        }

        List<Laureate> matches = new ArrayList<>();
        // Simulate: create two laureates that match subscriber filters for prototype
        Laureate l1 = new Laureate();
        l1.setLaureateId("bf-" + subscriber.getTechnicalId() + "-1");
        l1.setFullName("Backfill Laureate 1");
        l1.setYear(2019);
        l1.setCategory("Physics");

        Laureate l2 = new Laureate();
        l2.setLaureateId("bf-" + subscriber.getTechnicalId() + "-2");
        l2.setFullName("Backfill Laureate 2");
        l2.setYear(2018);
        l2.setCategory("Chemistry");

        matches.add(l1);
        matches.add(l2);

        // For prototype attach a simple summary to subscriber.meta
        Map<String, Object> meta = Map.of("backfillMatches", matches.size());
        subscriber.setMeta(meta);
        logger.info("Subscriber {} backfill found {} matches (from={})", subscriber.getTechnicalId(), matches.size(), from);

        return subscriber;
    }
}
