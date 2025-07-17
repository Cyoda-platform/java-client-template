package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.serializer.ErrorInfo;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Component
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestRequest.class)
            .validate(DigestRequest::isValid, "Invalid DigestRequest entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestProcessor".equals(modelSpec.operationName()) &&
               "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequest processEntityLogic(DigestRequest dr) {
        logger.info("Processing DigestRequest technicalId={}", dr.getId());
        dr.setStatus(DigestRequest.Status.PROCESSING);
        try {
            // Normally you would update the entityService here but in processor context we return the modified entity

            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            // Simulate call to external service returning pets
            Pet[] pets = fetchPets(url);
            String dataJson = pets != null ? Arrays.toString(pets) : "[]";

            DigestData dd = new DigestData();
            dd.setDigestRequestId(dr.getId().toString());
            dd.setData(dataJson);
            dd.setFormat(DigestData.Format.HTML);
            dd.setCreatedAt(Instant.now());

            // Simulate saving DigestData entity
            UUID ddTechnicalId = saveDigestData(dd);
            dd.setId(ddTechnicalId);

            EmailDispatch ed = new EmailDispatch();
            ed.setDigestRequestId(dr.getId().toString());
            ed.setEmail(dr.getEmail());
            ed.setStatus(EmailDispatch.Status.PENDING);

            // Simulate saving EmailDispatch entity
            UUID edTechnicalId = saveEmailDispatch(ed);
            ed.setId(edTechnicalId);

            simulateEmailSend(ed);

            dr.setStatus(DigestRequest.Status.COMPLETED);

            logger.info("Completed DigestRequest technicalId={}", dr.getId());
        } catch (Exception ex) {
            logger.error("Error processing DigestRequest technicalId={}", dr.getId(), ex);
            dr.setStatus(DigestRequest.Status.FAILED);
        }
        return dr;
    }

    private Pet[] fetchPets(String url) {
        // Real HTTP call removed for processor context
        return new Pet[0];
    }

    private UUID saveDigestData(DigestData dd) {
        // Simulate saving DigestData and returning its id
        return UUID.randomUUID();
    }

    private UUID saveEmailDispatch(EmailDispatch ed) {
        // Simulate saving EmailDispatch and returning its id
        return UUID.randomUUID();
    }

    private void simulateEmailSend(EmailDispatch ed) {
        logger.info("Simulating email send to {}", ed.getEmail());
        try {
            Thread.sleep(500);
            ed.setStatus(EmailDispatch.Status.SENT);
            ed.setSentAt(Instant.now());
            // Simulate update
            logger.info("Email sent to {}", ed.getEmail());
        } catch (InterruptedException e) {
            logger.error("Email send interrupted", e);
            ed.setStatus(EmailDispatch.Status.FAILED);
            ed.setErrorMessage("Interrupted");
        }
    }

    private static class Pet {
        private Long id;
        private String name;
        private String status;
    }
}
