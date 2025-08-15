package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MatchingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MatchingProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for matching")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate l) {
        return l != null && l.getLaureateId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate l = context.entity();

        // Prototype: simulate matching by scanning a static list of subscribers
        List<Subscriber> subscribers = simulateQueryActiveSubscribers();
        List<String> matchedSubscriberIds = new ArrayList<>();

        for (Subscriber s : subscribers) {
            if (!Boolean.TRUE.equals(s.getActive())) continue;
            if (matchesFilter(s.getFilters(), l)) {
                matchedSubscriberIds.add(s.getTechnicalId());
                logger.info("Laureate {} matches subscriber {}", l.getLaureateId(), s.getTechnicalId());
            }
        }

        l.setProvenance(l.getProvenance() == null ? Map.of("matchedSubscribers", matchedSubscriberIds) : addMatchToProvenance(l.getProvenance(), matchedSubscriberIds));
        return l;
    }

    private List<Subscriber> simulateQueryActiveSubscribers() {
        Subscriber s1 = new Subscriber();
        s1.setTechnicalId("sub-1");
        s1.setActive(true);
        s1.setFilters(Map.of("category", "Physics"));

        Subscriber s2 = new Subscriber();
        s2.setTechnicalId("sub-2");
        s2.setActive(true);
        s2.setFilters(Map.of("year", Map.of("gte", 2020)));

        return List.of(s1, s2);
    }

    private boolean matchesFilter(Map<String, Object> filters, Laureate l) {
        if (filters == null || filters.isEmpty()) return true;
        Object cat = filters.get("category");
        if (cat != null && l.getCategory() != null && !cat.equals(l.getCategory())) return false;
        Object year = filters.get("year");
        if (year instanceof Map) {
            Map<?, ?> yr = (Map<?, ?>) year;
            if (yr.get("gte") instanceof Number) {
                if (l.getYear() == null || l.getYear() < ((Number) yr.get("gte")).intValue()) return false;
            }
            if (yr.get("lte") instanceof Number) {
                if (l.getYear() == null || l.getYear() > ((Number) yr.get("lte")).intValue()) return false;
            }
        }
        return true;
    }

    private Map<String, Object> addMatchToProvenance(Map<String, Object> provenance, List<String> matches) {
        provenance.put("matchedSubscribers", matches);
        return provenance;
    }
}
