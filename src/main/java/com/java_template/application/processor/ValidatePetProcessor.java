package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component
public class ValidatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidatePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();

        // Ensure required fields (name, species) are present (isValid already checked this),
        // but double-check defensively and set an actionable status for admin review if missing.
        boolean fieldsOk = true;
        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            fieldsOk = false;
            logger.warn("Pet validation failed: missing name (id={})", pet.getId());
        }
        if (pet.getSpecies() == null || pet.getSpecies().trim().isEmpty()) {
            fieldsOk = false;
            logger.warn("Pet validation failed: missing species (id={})", pet.getId());
        }

        // Validate image URLs are reachable when present
        List<String> unreachable = new ArrayList<>();
        if (pet.getImages() != null && !pet.getImages().isEmpty()) {
            for (String img : pet.getImages()) {
                if (img == null || img.trim().isEmpty()) {
                    unreachable.add(img);
                    continue;
                }
                boolean ok = isUrlReachable(img);
                if (!ok) {
                    unreachable.add(img);
                }
            }
        }

        // Decide resulting entity state based on validations:
        // - If required fields missing or any image unreachable => mark for admin review (leave in created/review state)
        // - Otherwise mark as validated to allow downstream media processing
        if (!fieldsOk || !unreachable.isEmpty()) {
            // Prefer a workflow-friendly state indicating manual review is needed
            pet.setStatus("review");

            // Annotate medicalNotes with validation diagnostics if possible (avoid inventing new fields)
            StringBuilder noteBuilder = new StringBuilder();
            if (pet.getMedicalNotes() != null && !pet.getMedicalNotes().isBlank()) {
                noteBuilder.append(pet.getMedicalNotes()).append(" | ");
            }
            noteBuilder.append("validation: ");
            if (!fieldsOk) noteBuilder.append("missing required fields; ");
            if (!unreachable.isEmpty()) noteBuilder.append("unreachable images=").append(unreachable);
            pet.setMedicalNotes(noteBuilder.toString());

            logger.info("Pet marked for review (id={}): fieldsOk={}, unreachableImages={}", pet.getId(), fieldsOk, unreachable.size());
        } else {
            // All validations passed -> tag as validated via status so that media processing can follow
            pet.setStatus("validating");
            logger.info("Pet validation passed (id={}), proceeding to media processing", pet.getId());
        }

        return pet;
    }

    private boolean isUrlReachable(String urlStr) {
        try {
            URL url = new URL(urlStr);
            // Only attempt for http/https
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception ex) {
            logger.debug("Image URL check failed for '{}': {}", urlStr, ex.getMessage());
            return false;
        }
    }
}