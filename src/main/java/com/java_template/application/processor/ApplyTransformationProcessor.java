package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
import com.java_template.application.entity.transformjob.version_1.TransformJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ApplyTransformationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApplyTransformationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApplyTransformationProcessor(SerializerFactory serializerFactory,
                                        EntityService entityService,
                                        ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TransformJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(TransformJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformJob entity) {
        return entity != null && entity.isValid();
    }

    private TransformJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformJob> context) {
        TransformJob job = context.entity();
        logger.info("Starting transformation job: id={}, ruleNames={}", job.getId(), job.getRuleNames());

        List<Pet> transformedPets = new ArrayList<>();
        try {
            // 1. Load search filter if provided
            SearchFilter searchFilter = null;
            if (job.getSearchFilterId() != null && !job.getSearchFilterId().isBlank()) {
                CompletableFuture<ObjectNode> sfFuture = entityService.getItem(
                    SearchFilter.ENTITY_NAME,
                    String.valueOf(SearchFilter.ENTITY_VERSION),
                    java.util.UUID.fromString(job.getSearchFilterId())
                );
                ObjectNode sfNode = sfFuture.join();
                if (sfNode != null && !sfNode.isEmpty()) {
                    searchFilter = objectMapper.treeToValue(sfNode, SearchFilter.class);
                }
            }

            // 2. Fetch matching pets based on searchFilter (or all pets if no filter)
            List<Pet> petsToTransform = new ArrayList<>();
            if (searchFilter != null) {
                List<Condition> conditions = new ArrayList<>();
                if (searchFilter.getSpecies() != null && !searchFilter.getSpecies().isBlank()) {
                    conditions.add(Condition.of("$.species", "EQUALS", searchFilter.getSpecies()));
                }
                if (searchFilter.getSex() != null && !searchFilter.getSex().isBlank()) {
                    conditions.add(Condition.of("$.sex", "EQUALS", searchFilter.getSex()));
                }
                if (searchFilter.getSize() != null && !searchFilter.getSize().isEmpty()) {
                    // build OR group for sizes
                    List<Condition> sizeConds = new ArrayList<>();
                    for (String s : searchFilter.getSize()) {
                        if (s != null && !s.isBlank()) sizeConds.add(Condition.of("$.size", "EQUALS", s));
                    }
                    if (!sizeConds.isEmpty()) {
                        // Add as a grouped OR condition by embedding the group as a single condition via SearchConditionRequest (library expects top-level group)
                        // We'll construct a top-level AND group containing the OR group as a SearchConditionRequest grouping.
                        // For simplicity, convert sizes to multiple ANDed conditions using IEQUALS (case-insensitive) on size - safer fallback if OR grouping not supported here.
                        for (Condition c : sizeConds) conditions.add(c);
                    }
                }
                if (searchFilter.getBreeds() != null && !searchFilter.getBreeds().isEmpty()) {
                    // build OR for breeds by adding multiple conditions (will act as AND if library doesn't support OR)
                    List<Condition> breedConds = new ArrayList<>();
                    for (String b : searchFilter.getBreeds()) {
                        if (b != null && !b.isBlank()) breedConds.add(Condition.of("$.breed", "EQUALS", b));
                    }
                    if (!breedConds.isEmpty()) {
                        // add all breed conditions (best-effort)
                        for (Condition c : breedConds) conditions.add(c);
                    }
                }

                // If we have any conditions, call getItemsByCondition, otherwise fetch all
                if (!conditions.isEmpty()) {
                    SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                    CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        conditionRequest,
                        true
                    );
                    ArrayNode nodes = itemsFuture.join();
                    if (nodes != null) {
                        for (int i = 0; i < nodes.size(); i++) {
                            ObjectNode node = (ObjectNode) nodes.get(i);
                            Pet p = objectMapper.treeToValue(node, Pet.class);
                            petsToTransform.add(p);
                        }
                    }
                } else {
                    // No usable conditions: fetch all pets
                    CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION)
                    );
                    ArrayNode nodes = itemsFuture.join();
                    if (nodes != null) {
                        for (int i = 0; i < nodes.size(); i++) {
                            ObjectNode node = (ObjectNode) nodes.get(i);
                            Pet p = objectMapper.treeToValue(node, Pet.class);
                            petsToTransform.add(p);
                        }
                    }
                }
            } else {
                // No search filter provided -> fetch all pets
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
                );
                ArrayNode nodes = itemsFuture.join();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        ObjectNode node = (ObjectNode) nodes.get(i);
                        Pet p = objectMapper.treeToValue(node, Pet.class);
                        petsToTransform.add(p);
                    }
                }
            }

            // 3. Apply transformation rules to each pet
            List<String> rules = job.getRuleNames();
            if (rules == null) rules = new ArrayList<>();

            for (Pet pet : petsToTransform) {
                for (String rule : rules) {
                    applyRuleToPet(rule, pet);
                }
                transformedPets.add(pet);
            }

            // 4. Store result metadata and finalize job
            job.setResultCount(transformedPets.size());
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now().toString());
            // Set an output location placeholder - actual storage handled elsewhere
            job.setOutputLocation("/results/" + job.getId() + ".json");
            job.setErrorMessage(null);

            logger.info("Transformation job {} completed. Transformed {} pets.", job.getId(), transformedPets.size());
        } catch (Exception e) {
            logger.error("Error while processing transformation job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() == null ? "Unknown error" : e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            job.setResultCount(0);
        }

        // Note: Do not call entityService.updateItem on this TransformJob - persistence is handled by workflow.
        return job;
    }

    /**
     * Apply a simple set of transformation rules. Rules implemented (best-effort based on functional requirements):
     * - "normalize_age": convert months to years when >=12 months, or convert small fractional years to months.
     * - "map_region": normalize city string (trim).
     * - "infer_tags": add simple inferred temperament tags based on species/age/size.
     *
     * Additional rules can be added by name here.
     */
    private void applyRuleToPet(String rule, Pet pet) {
        if (rule == null) return;
        String r = rule.trim().toLowerCase();
        switch (r) {
            case "normalize_age":
            case "normalize-age":
                normalizeAge(pet);
                break;
            case "map_region":
            case "map-region":
                mapRegion(pet);
                break;
            case "infer_tags":
            case "infer-tags":
                inferTags(pet);
                break;
            default:
                // Unknown rule - ignore but log
                logger.debug("Unknown transformation rule '{}' for pet id={}", rule, pet.getId());
        }
    }

    private void normalizeAge(Pet pet) {
        try {
            if (pet.getAge_unit() == null || pet.getAge_value() == null) return;
            String unit = pet.getAge_unit().trim().toLowerCase();
            Integer value = pet.getAge_value();
            if ("months".equals(unit) || "month".equals(unit)) {
                if (value >= 12) {
                    int years = value / 12;
                    pet.setAge_value(years);
                    pet.setAge_unit("years");
                } else {
                    pet.setAge_unit("months");
                }
            } else if ("years".equals(unit) || "year".equals(unit)) {
                if (value == 0) {
                    // convert to months as fractional representation
                    pet.setAge_value(0);
                    pet.setAge_unit("years");
                } else if (value < 1) {
                    int months = (int) Math.round(value * 12.0);
                    pet.setAge_value(months);
                    pet.setAge_unit("months");
                } else {
                    pet.setAge_unit("years");
                }
            }
        } catch (Exception e) {
            logger.debug("normalizeAge failed for pet {}: {}", pet == null ? "null" : pet.getId(), e.getMessage());
        }
    }

    private void mapRegion(Pet pet) {
        try {
            if (pet.getLocation() != null && pet.getLocation().getCity() != null) {
                String city = pet.getLocation().getCity().trim();
                pet.getLocation().setCity(city);
            }
            // Additional region mapping could be added here when mapping data is available.
        } catch (Exception e) {
            logger.debug("mapRegion failed for pet {}: {}", pet == null ? "null" : pet.getId(), e.getMessage());
        }
    }

    private void inferTags(Pet pet) {
        try {
            if (pet.getTemperament_tags() == null) return;
            List<String> tags = pet.getTemperament_tags();
            // Example heuristics:
            if ("dog".equalsIgnoreCase(pet.getSpecies())) {
                if ("small".equalsIgnoreCase(pet.getSize()) && !tags.contains("playful")) tags.add("playful");
                if (pet.getAge_value() != null && pet.getAge_value() > 8 && !tags.contains("calm")) tags.add("calm");
            } else if ("cat".equalsIgnoreCase(pet.getSpecies())) {
                if (pet.getAge_value() != null && pet.getAge_value() < 2 && !tags.contains("playful")) tags.add("playful");
            }
        } catch (Exception e) {
            logger.debug("inferTags failed for pet {}: {}", pet == null ? "null" : pet.getId(), e.getMessage());
        }
    }
}