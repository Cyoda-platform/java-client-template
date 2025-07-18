package com.java_template.application.processor;

import com.java_template.application.entity.DigestJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class DigestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public DigestJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestJob.class)
                .validate(DigestJob::isValid, "Invalid DigestJob entity state")
                .map(this::processDigestJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestJobProcessor".equals(modelSpec.operationName()) &&
                "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestJob processDigestJobLogic(DigestJob digestJob) {
        try {
            logger.info("Processing DigestJob with technicalId: {}", digestJob.getTechnicalId());

            if (digestJob.getPetDataQuery() == null || digestJob.getPetDataQuery().isBlank()) {
                logger.error("DigestJob petDataQuery is blank");
                digestJob.setStatus("FAILED");
                entityService.updateItem("ENTITY_DIGEST_JOB", Config.ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();
                return digestJob;
            }
            if (digestJob.getEmailRecipients() == null || digestJob.getEmailRecipients().isEmpty()) {
                logger.error("DigestJob emailRecipients list is empty");
                digestJob.setStatus("FAILED");
                entityService.updateItem("ENTITY_DIGEST_JOB", Config.ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();
                return digestJob;
            }

            digestJob.setStatus("PROCESSING");
            entityService.updateItem("ENTITY_DIGEST_JOB", Config.ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();

            List<com.java_template.application.entity.PetDataRecord> fetchedPetData = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                com.java_template.application.entity.PetDataRecord petDataRecord = new com.java_template.application.entity.PetDataRecord();
                petDataRecord.setJobId(digestJob.getId());
                petDataRecord.setPetId(100 + i);
                petDataRecord.setName("PetName" + i);
                petDataRecord.setCategory("Category" + i);
                petDataRecord.setStatus("NEW");

                UUID technicalId = entityService.addItem(
                        "ENTITY_PET_DATA_RECORD",
                        Config.ENTITY_VERSION,
                        petDataRecord
                ).get();
                petDataRecord.setTechnicalId(technicalId);
                fetchedPetData.add(petDataRecord);

                processPetDataRecordLogic(petDataRecord);
            }

            for (String recipient : digestJob.getEmailRecipients()) {
                com.java_template.application.entity.EmailDispatch emailDispatch = new com.java_template.application.entity.EmailDispatch();
                String id = java.util.UUID.randomUUID().toString();
                emailDispatch.setId(id);
                emailDispatch.setTechnicalId(java.util.UUID.randomUUID());
                emailDispatch.setJobId(digestJob.getId());
                emailDispatch.setRecipient(recipient);
                emailDispatch.setSubject("Pet Data Digest");
                emailDispatch.setBody("Digest of pet data for your query: " + digestJob.getPetDataQuery());
                emailDispatch.setStatus("QUEUED");

                processEmailDispatchLogic(emailDispatch);
            }

            digestJob.setStatus("COMPLETED");
            entityService.updateItem("ENTITY_DIGEST_JOB", Config.ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();

            logger.info("Completed processing DigestJob with technicalId: {}", digestJob.getTechnicalId());
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing DigestJob", e);
            digestJob.setStatus("FAILED");
            try {
                entityService.updateItem("ENTITY_DIGEST_JOB", Config.ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();
            } catch (Exception ex) {
                logger.error("Failed to update DigestJob status to FAILED", ex);
            }
        }
        return digestJob;
    }

    private void processPetDataRecordLogic(com.java_template.application.entity.PetDataRecord petDataRecord) throws ExecutionException, InterruptedException {
        logger.info("Processing PetDataRecord with technicalId: {}", petDataRecord.getTechnicalId());

        petDataRecord.setStatus("PROCESSED");
        entityService.updateItem("ENTITY_PET_DATA_RECORD", Config.ENTITY_VERSION, petDataRecord.getTechnicalId(), petDataRecord).get();

        logger.info("PetDataRecord processed with technicalId: {}", petDataRecord.getTechnicalId());
    }

    private void processEmailDispatchLogic(com.java_template.application.entity.EmailDispatch emailDispatch) {
        logger.info("Processing EmailDispatch with ID: {}", emailDispatch.getId());

        try {
            emailDispatch.setStatus("SENT");
            logger.info("Email sent to {}", emailDispatch.getRecipient());
        } catch (Exception e) {
            logger.error("Failed to send email to {}", emailDispatch.getRecipient(), e);
            emailDispatch.setStatus("FAILED");
        }

        // Assuming there is a cache or service to update EmailDispatch entity
        // This is a simplified example, adapt as needed
        // emailDispatchCache.put(emailDispatch.getId(), emailDispatch);
    }
}
