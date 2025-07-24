package com.java_template.application.processor;

import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.application.entity.Pet;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PetRegistrationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final RestTemplate restTemplate;
    private final com.java_template.common.service.EntityService entityService;

    public PetRegistrationJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        logger.info("PetRegistrationJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetRegistrationJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetRegistrationJob.class)
                .validate(PetRegistrationJob::isValid, "Invalid PetRegistrationJob state")
                .map(this::processPetRegistrationJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRegistrationJobProcessor".equals(modelSpec.operationName()) &&
                "petRegistrationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetRegistrationJob processPetRegistrationJobLogic(PetRegistrationJob job) {
        logger.info("Processing PetRegistrationJob with petName: {}", job.getPetName());
        if (job.getPetName().isBlank() || job.getPetType().isBlank() || job.getOwnerName().isBlank()) {
            job.setStatus("FAILED");
            logger.error("PetRegistrationJob validation failed for petName: {}", job.getPetName());
            return job;
        }
        job.setStatus("PROCESSING");

        // Fetch pets from public API (Petstore Swagger)
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

        try {
            ResponseEntity<Pet[]> response = restTemplate.getForEntity(url, Pet[].class);
            Pet[] petsFromApi = response.getBody();
            if (petsFromApi != null) {
                List<Pet> petList = new ArrayList<>();
                for (Pet apiPet : petsFromApi) {
                    if (apiPet.getPetId() == null || apiPet.getPetId().isBlank()) {
                        apiPet.setPetId(UUID.randomUUID().toString());
                    }
                    petList.add(apiPet);
                }
                entityService.addItems("Pet", Config.ENTITY_VERSION, petList).get();
                logger.info("Saved {} Pets from public API", petList.size());
            }
        } catch (Exception e) {
            logger.error("Failed to fetch pets from public API: {}", e.getMessage());
            job.setStatus("FAILED");
            throw new RuntimeException(e);
        }

        job.setStatus("COMPLETED");
        logger.info("PetRegistrationJob processed successfully for petName: {}", job.getPetName());
        return job;
    }

}
