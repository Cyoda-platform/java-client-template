package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class FetchCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String EXTERNAL_API = "https://catfact.ninja/fact"; // example

    public FetchCatFactProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchCatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorExecutionContext context) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(EXTERNAL_API))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> body = mapper.readValue(response.body(), Map.class);
                String text = (String) body.getOrDefault("fact", "");
                if (text == null || text.isBlank()) {
                    logger.warn("Fetched cat fact empty");
                    return null;
                }
                CatFact catFact = new CatFact();
                catFact.setId(UUID.randomUUID().toString());
                catFact.setText(text);
                catFact.setSource(EXTERNAL_API);
                catFact.setRetrieved_date(OffsetDateTime.now().toString());
                catFact.setArchived(false);
                // compute simple hash
                String normalized = text.trim().toLowerCase();
                catFact.setText_hash(Integer.toString(normalized.hashCode()));
                catFact.setStatus("ingested");

                // Persist via entityService
                entityService.addItem(CatFact.ENTITY_NAME, String.valueOf(CatFact.ENTITY_VERSION), catFact);
                logger.info("Ingested new CatFact id={} text_hash={}", catFact.getId(), catFact.getText_hash());
            } else {
                logger.error("Failed to fetch cat fact, status {}", response.statusCode());
            }
        } catch (Exception ex) {
            logger.error("Error fetching cat fact: {}", ex.getMessage(), ex);
        }
        return null;
    }
}
