package com.java_template.application.processor;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EnrichmentProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for enrichment")
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

        // compute matchTags - simple tokenization of fullName + category
        List<String> tags = new ArrayList<>();
        if (l.getFullName() != null) {
            for (String token : l.getFullName().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (token.length() > 2) tags.add(token);
            }
        }
        if (l.getCategory() != null) {
            tags.add(l.getCategory().toLowerCase(Locale.ROOT));
        }
        l.setMatchTags(tags);

        // normalize affiliations to simple lowercase unique list
        if (l.getAffiliations() != null) {
            List<String> aff = new ArrayList<>();
            for (String a : l.getAffiliations()) {
                if (a == null) continue;
                String norm = a.trim().toLowerCase(Locale.ROOT);
                if (!aff.contains(norm)) aff.add(norm);
            }
            l.setAffiliations(aff);
        }

        // Add simple provenance enrichment if missing
        if (l.getProvenance() == null) {
            l.setProvenance(Map.of("enrichedAt", java.time.Instant.now().toString()));
        } else {
            Map<String, Object> p = l.getProvenance();
            p.put("enrichedAt", java.time.Instant.now().toString());
            l.setProvenance(p);
        }

        logger.info("Enriched laureate {} tags={} affiliations={} ", l.getLaureateId(), tags.size(), l.getAffiliations() == null ? 0 : l.getAffiliations().size());
        return l;
    }
}
