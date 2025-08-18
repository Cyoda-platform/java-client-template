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

import java.text.Normalizer;
import java.util.Locale;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateEnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LaureateEnrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Laureate missing required fields for enrichment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getFullName() != null && !laureate.getFullName().isBlank();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Normalize name into given and family if possible
        try {
            String full = laureate.getFullName();
            if (full != null) {
                String normalized = Normalizer.normalize(full, Normalizer.Form.NFKC).trim();
                laureate.setFullName(normalized);
                // crude split: last token as family name
                String[] parts = normalized.split("\\s+");
                if (parts.length > 1) {
                    laureate.setFamilyName(parts[parts.length - 1]);
                    laureate.setGivenName(String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)));
                } else {
                    laureate.setGivenName(parts[0]);
                }
            }
            // generate normalizedFingerprint: simple lowercased concatenation
            StringBuilder fp = new StringBuilder();
            if (laureate.getFullName() != null) fp.append(laureate.getFullName().toLowerCase(Locale.ROOT).replaceAll("\\s+",""));
            if (laureate.getYear() != null) fp.append(laureate.getYear());
            if (laureate.getCategory() != null) fp.append(laureate.getCategory().toLowerCase(Locale.ROOT));
            laureate.setNormalizedFingerprint(fp.toString());

            // Normalize country to upper case ISO-like
            if (laureate.getCountry() != null) {
                laureate.setCountry(laureate.getCountry().trim());
            }

            logger.info("Enriched laureate {} with fingerprint {}", laureate.getFullName(), laureate.getNormalizedFingerprint());
        } catch (Exception e) {
            logger.warn("Error enriching laureate {}: {}", laureate, e.getMessage());
        }
        return laureate;
    }
}
