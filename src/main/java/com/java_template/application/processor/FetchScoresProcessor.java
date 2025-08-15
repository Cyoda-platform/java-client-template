package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchScoresProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchScoresProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final HttpUtils httpUtils;
    private final JsonUtils jsonUtils;

    private static final String API_BASE = "https://api.sportsdata.io/v3/nba/scores/json";
    private static final String API_KEY = "test";

    public FetchScoresProcessor(SerializerFactory serializerFactory, HttpUtils httpUtils, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.httpUtils = httpUtils;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob fetch scores for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FetchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob entity) {
        return entity != null && entity.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob job = context.entity();
        try {
            String date = job.getRequestDate();
            String path = "ScoresBasicFinal/" + date + "?key=" + API_KEY;
            String urlPath = "ScoresBasicFinal/" + date;
            // Use HttpUtils to call external API. Implement simple retry for 5xx up to 2 retries
            int maxAttempts = 3;
            int attempt = 0;
            while (attempt < maxAttempts) {
                attempt++;
                try {
                    CompletableFuture<ObjectNode> respFuture = httpUtils.sendGetRequest(null, API_BASE, urlPath);
                    ObjectNode resp = respFuture.get();
                    int status = resp.has("status") ? resp.get("status").asInt() : 0;
                    if (status >= 200 && status < 300) {
                        job.setResponsePayload(jsonUtils.toJson(resp.get("json")));
                        // leave status for later processors
                        logger.info("Fetch succeeded for date {}", date);
                        break;
                    } else if (status == 429) {
                        // respect Retry-After header not available via HttpUtils; backoff and retry
                        logger.warn("Rate limited when fetching scores for {}", date);
                        Thread.sleep(1000L * attempt);
                    } else if (status >= 500) {
                        logger.warn("Server error {} when fetching scores for {}, attempt {}/{}", status, date, attempt, maxAttempts);
                        Thread.sleep(500L * attempt);
                        if (attempt == maxAttempts) {
                            job.setStatus("FAILED");
                            job.setErrorMessage("External API returned status " + status);
                        }
                    } else {
                        // Client error - do not retry
                        job.setStatus("FAILED");
                        job.setErrorMessage("External API returned status " + status);
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("Exception fetching scores attempt {}/{}: {}", attempt, maxAttempts, e.getMessage());
                    if (attempt == maxAttempts) {
                        job.setStatus("FAILED");
                        job.setErrorMessage(e.getMessage());
                    }
                }
            }
            job.setCompletedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error in FetchScoresProcessor: {}", e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        return job;
    }
}
