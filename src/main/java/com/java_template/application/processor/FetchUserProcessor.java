package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.application.entity.user.version_1.User;
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;

@Component
public class FetchUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FetchUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LookupJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(LookupJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(LookupJob entity) {
        return entity != null && entity.isValid();
    }

    private LookupJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<LookupJob> context) {
        LookupJob job = context.entity();
        Integer userId = job.getUserId();
        if (userId == null) {
            logger.warn("FetchUserProcessor: userId is null for job={}", job.getTechnicalId());
            // attach fetchResponse with 400
            job.setFetchResponse(createResponseNode(400, null, "userId missing"));
            return job;
        }

        String urlStr = String.format("https://reqres.in/api/users/%d", userId);
        HttpURLConnection conn = null;
        int status = 0;
        String details = null;
        JsonNode bodyNode = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            status = conn.getResponseCode();
            if (status == 200) {
                bodyNode = objectMapper.readTree(conn.getInputStream()).path("data");
            }
        } catch (IOException e) {
            logger.debug("FetchUserProcessor network error for job={}: {}", job.getTechnicalId(), e.getMessage());
            details = e.getMessage();
            status = 503;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        // attach fetchResponse as JSON string
        job.setFetchResponse(createResponseNode(status, bodyNode, details));
        job.setLastAttemptAt(Instant.now().toString());
        return job;
    }

    private String createResponseNode(int status, JsonNode body, String details) {
        try {
            if (body == null) {
                return objectMapper.createObjectNode().put("status", status)
                    .put("details", details == null ? "" : details).toString();
            }
            return objectMapper.createObjectNode().put("status", status).set("body", body).toString();
        } catch (Exception e) {
            logger.debug("FetchUserProcessor: error creating response node: {}", e.getMessage());
            return String.format("{\"status\":%d,\"details\":\"%s\"}", status, details == null ? "" : details);
        }
    }
}
