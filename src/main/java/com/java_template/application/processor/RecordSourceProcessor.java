package com.java_template.application.processor;
import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HexFormat;
import java.security.MessageDigest;

@Component
public class RecordSourceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordSourceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecordSourceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(DataSource.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataSource entity) {
        if (entity == null) return false;
        // For recording a source we require at least id and url to be present.
        if (entity.getId() == null || entity.getId().isBlank()) return false;
        if (entity.getUrl() == null || entity.getUrl().isBlank()) return false;
        return true;
    }

    private DataSource processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataSource> context) {
        DataSource entity = context.entity();
        if (entity == null) return null;

        String url = entity.getUrl();
        if (url == null || url.isBlank()) {
            logger.warn("DataSource url is blank for id={}", entity.getId());
            entity.setValidationStatus("INVALID");
            return entity;
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            logger.info("Fetching data source from URL: {} for id={}", url, entity.getId());
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                byte[] body = response.body();
                // compute sample hash (SHA-256)
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(body);
                String hash = HexFormat.of().formatHex(digest);

                // attempt to infer schema from first line (header)
                String content = new String(body);
                String schema = "";
                String[] lines = content.split("\\r?\\n", -1);
                if (lines.length > 0 && lines[0] != null && !lines[0].isBlank()) {
                    // preserve header as schema string (comma separated)
                    schema = lines[0].trim();
                }

                // set values on the entity
                entity.setSampleHash(hash);
                entity.setSchema(schema);
                entity.setLastFetchedAt(Instant.now().toString());
                // mark as FETCHED to indicate fetch completed; validation will be done by SchemaCheckProcessor
                entity.setValidationStatus("FETCHED");

                logger.info("Fetched DataSource id={}, sampleHash={}, schemaHeader={}", entity.getId(), hash, schema);
            } else {
                logger.error("Failed to fetch data source id={}, url={}, status={}", entity.getId(), url, status);
                entity.setValidationStatus("INVALID");
            }
        } catch (Exception ex) {
            logger.error("Exception while fetching data source id={} url={}: {}", entity.getId(), url, ex.getMessage(), ex);
            entity.setValidationStatus("INVALID");
        }

        return entity;
    }
}