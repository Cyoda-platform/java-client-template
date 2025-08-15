package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public EnrichmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

        // compute matchTags - tokenize fullName + category
        Set<String> tags = new LinkedHashSet<>();
        if (l.getFullName() != null) {
            String[] tokens = l.getFullName().toLowerCase(Locale.ROOT).split("\\s+");
            for (String t : tokens) {
                if (t.length() > 2) tags.add(t.replaceAll("[^a-z0-9]", ""));
            }
        }
        if (l.getCategory() != null) {
            tags.add(l.getCategory().toLowerCase(Locale.ROOT));
        }
        // persist as comma-separated string in matchTags field (entity model uses String)
        l.setMatchTags(String.join(",", tags));

        // normalize affiliations: input may be comma-separated string; normalize to unique lowercase list
        String affIn = l.getAffiliations();
        if (affIn != null && !affIn.isBlank()) {
            List<String> parts = Arrays.stream(affIn.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
            l.setAffiliations(String.join(",", parts));
        }

        // Add simple provenance enrichment stored in sourceRecord (JSON string) to avoid adding new entity fields
        try {
            ObjectNode prov;
            if (l.getSourceRecord() == null || l.getSourceRecord().isBlank()) {
                prov = objectMapper.createObjectNode();
            } else {
                JsonNode existing = objectMapper.readTree(l.getSourceRecord());
                prov = existing.isObject() ? (ObjectNode) existing : objectMapper.createObjectNode();
            }
            prov.put("enrichedAt", java.time.Instant.now().toString());
            prov.put("matchTagsCount", tags.size());
            l.setSourceRecord(objectMapper.writeValueAsString(prov));
        } catch (Exception e) {
            logger.warn("Failed to write provenance for laureate {}: {}", l.getLaureateId(), e.getMessage());
        }

        logger.info("Enriched laureate {} tags={} affiliations={} ", l.getLaureateId(), tags.size(), l.getAffiliations() == null ? 0 : l.getAffiliations().split(",").length);
        return l;
    }
}
