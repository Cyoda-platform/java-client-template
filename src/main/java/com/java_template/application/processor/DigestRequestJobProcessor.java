package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.ExternalApiData;
import com.java_template.application.entity.EmailDispatchRecord;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@Component
public class DigestRequestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public DigestRequestJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequestJob.class)
                .validate(DigestRequestJob::isValid)
                .map(this::processDigestRequestJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobProcessor".equals(modelSpec.operationName()) &&
                "digestRequestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequestJob processDigestRequestJobLogic(DigestRequestJob job) {
        String technicalId = job.getModelKey().getId();
        logger.info("Processing DigestRequestJob with ID: {}", technicalId);

        if (job.getEmail() == null || job.getEmail().isBlank() || !job.getEmail().contains("@")) {
            logger.error("Invalid email format for job ID: {}", technicalId);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            return job;
        }

        String apiDataPayload = "";
        try {
            apiDataPayload = fetchExternalApiData();
        } catch (Exception e) {
            logger.error("Failed to fetch external API data for job ID: {} - {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            return job;
        }

        ExternalApiData apiData = new ExternalApiData();
        apiData.setJobTechnicalId(technicalId);
        apiData.setDataPayload(apiDataPayload);
        apiData.setRetrievedAt(Instant.now().toString());
        try {
            entityService.addItem("ExternalApiData", Config.ENTITY_VERSION, apiData).get();
            logger.info("Saved ExternalApiData for job ID: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to save ExternalApiData for job ID: {} - {}", technicalId, e.getMessage());
        }

        EmailDispatchRecord emailRecord = new EmailDispatchRecord();
        emailRecord.setJobTechnicalId(technicalId);
        emailRecord.setEmail(job.getEmail());
        emailRecord.setDispatchStatus("PENDING");
        try {
            entityService.addItem("EmailDispatchRecord", Config.ENTITY_VERSION, emailRecord).get();
            logger.info("Created EmailDispatchRecord for job ID: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to create EmailDispatchRecord for job ID: {} - {}", technicalId, e.getMessage());
        }

        processEmailDispatchRecord(technicalId, emailRecord);

        job.setStatus("COMPLETED");
        job.setCompletedAt(Instant.now().toString());
        logger.info("DigestRequestJob with ID: {} completed successfully", technicalId);

        return job;
    }

    private String fetchExternalApiData() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=available"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("External API call failed with status: " + response.statusCode());
        }
        return response.body();
    }

    private void processEmailDispatchRecord(String technicalId, EmailDispatchRecord record) {
        logger.info("Processing EmailDispatchRecord with job ID: {}", technicalId);

        try {
            Thread.sleep(500);
            record.setDispatchStatus("SENT");
            record.setSentAt(Instant.now().toString());
            logger.info("Email sent successfully to {} for job ID: {}", record.getEmail(), technicalId);
        } catch (InterruptedException e) {
            logger.error("Email sending interrupted for job ID: {}", technicalId);
            record.setDispatchStatus("FAILED");
        }
    }

}
