package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.SuiteDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Suite operations
 */
@Service
public class SuiteService {

    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(SuiteDTO.ENTITY_NAME).withVersion(SuiteDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SuiteService(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private SuiteDTO withId(EntityWithMetadata<SuiteDTO> result) {
        SuiteDTO entity = result.entity();
        entity.setId(result.getId());
        return entity;
    }

    private PageResult<SuiteDTO> toPage(PageResult<EntityWithMetadata<SuiteDTO>> result) {
        return PageResult.of(result.searchId(),
                result.data().stream().map(this::withId).toList(),
                result.pageNumber(), result.pageSize(), result.totalElements());
    }

    private GroupCondition conditionByField(String fieldName, Object value) {
        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$." + fieldName)
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(value));
        return new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));
    }

    /**
     * Creates a new suite with ACTIVE status
     */
    public SuiteDTO createSuite(SuiteDTO suite) {
        suite.setStatus("ACTIVE");
        return withId(entityService.create(suite));
    }

    /**
     * Retrieves a suite by ID
     */
    public Optional<SuiteDTO> getSuiteById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, SuiteDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves all suites for a specific project
     */
    public PageResult<SuiteDTO> getSuitesByProjectId(UUID projectId, int page, int size) {
        SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                .pageNumber(page).pageSize(size).build();
        return toPage(entityService.search(MODEL_SPEC, conditionByField("projectId", projectId.toString()),
                SuiteDTO.class, params));
    }

    /**
     * Retrieves all suites
     */
    public List<SuiteDTO> getAllSuites() {
        return entityService.findAll(MODEL_SPEC, SuiteDTO.class).data()
                .stream().map(this::withId).toList();
    }

    /**
     * Updates an existing suite
     */
    public SuiteDTO updateSuite(UUID id, SuiteDTO suite) {
        return withId(entityService.update(id, suite, null));
    }

    /**
     * Deletes a suite by ID
     */
    public boolean deleteSuite(UUID id) {
        entityService.deleteById(id);
        return true;
    }

    /**
     * Checks if a suite exists by ID
     */
    public boolean suiteExists(UUID id) {
        return getSuiteById(id).isPresent();
    }
}

