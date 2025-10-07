package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.dto.EngineOptions;
import com.java_template.application.controller.dto.TransitionRequest;
import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for EODAccrualBatchController
 */
@SpringBootTest
@AutoConfigureMockMvc
class EODAccrualBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EntityService entityService;

    private EODAccrualBatch testBatch;
    private EntityWithMetadata<EODAccrualBatch> testBatchWithMetadata;
    private UUID testTechnicalId;

    @BeforeEach
    void setUp() {
        testTechnicalId = UUID.randomUUID();

        // Create test batch
        testBatch = new EODAccrualBatch();
        testBatch.setBatchId(UUID.randomUUID());
        testBatch.setAsOfDate(LocalDate.of(2025, 8, 15));
        testBatch.setMode(BatchMode.BACKDATED);
        testBatch.setInitiatedBy("user@example.com");
        testBatch.setReasonCode("DATA_CORRECTION");
        testBatch.setState(EODAccrualBatchState.REQUESTED);

        // Create metrics
        BatchMetrics metrics = new BatchMetrics();
        metrics.setEligibleLoans(0);
        metrics.setProcessedLoans(0);
        metrics.setAccrualsCreated(0);
        testBatch.setMetrics(metrics);

        // Create metadata
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(testTechnicalId);
        metadata.setState("REQUESTED");
        metadata.setCreationDate(new java.util.Date());

        testBatchWithMetadata = new EntityWithMetadata<>(testBatch, metadata);
    }

    @Test
    @DisplayName("POST /eod-batches should create a new batch")
    void testCreateBatch() throws Exception {
        // Given
        when(entityService.create(any(EODAccrualBatch.class))).thenReturn(testBatchWithMetadata);

        EODAccrualBatchController.CreateBatchRequest request = new EODAccrualBatchController.CreateBatchRequest();
        request.setBatch(testBatch);

        // When/Then
        mockMvc.perform(post("/eod-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$.entity.mode").value("BACKDATED"))
                .andExpect(jsonPath("$.entity.reasonCode").value("DATA_CORRECTION"))
                .andExpect(jsonPath("$.metadata.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("POST /eod-batches with START transition should create and start batch (section 7.1 example)")
    void testCreateBatchWithStartTransition() throws Exception {
        // Given - This is the example from section 7.1 of the requirements
        EntityMetadata runningMetadata = new EntityMetadata();
        runningMetadata.setId(testTechnicalId);
        runningMetadata.setState("GENERATING");
        runningMetadata.setCreationDate(new java.util.Date());

        EODAccrualBatch runningBatch = new EODAccrualBatch();
        runningBatch.setBatchId(testBatch.getBatchId());
        runningBatch.setAsOfDate(LocalDate.of(2025, 8, 15));
        runningBatch.setMode(BatchMode.BACKDATED);
        runningBatch.setReasonCode("DATA_CORRECTION");
        runningBatch.setState(EODAccrualBatchState.GENERATING);

        BatchMetrics metrics = new BatchMetrics();
        metrics.setEligibleLoans(100);
        metrics.setProcessedLoans(0);
        metrics.setAccrualsCreated(0);
        runningBatch.setMetrics(metrics);

        EntityWithMetadata<EODAccrualBatch> runningWithMetadata =
            new EntityWithMetadata<>(runningBatch, runningMetadata);

        when(entityService.create(any(EODAccrualBatch.class))).thenReturn(testBatchWithMetadata);
        when(entityService.update(eq(testTechnicalId), any(EODAccrualBatch.class), eq("START")))
            .thenReturn(runningWithMetadata);

        EODAccrualBatchController.CreateBatchRequest request = new EODAccrualBatchController.CreateBatchRequest();
        request.setBatch(testBatch);

        TransitionRequest transitionRequest = new TransitionRequest();
        transitionRequest.setName("START");
        request.setTransitionRequest(transitionRequest);

        EngineOptions engineOptions = new EngineOptions();
        engineOptions.setSimulate(false);
        engineOptions.setMaxSteps(50);
        request.setEngineOptions(engineOptions);

        // When/Then
        mockMvc.perform(post("/eod-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.state").value("GENERATING"))
                .andExpect(jsonPath("$.entity.metrics.eligibleLoans").value(100));
    }

    @Test
    @DisplayName("POST /eod-batches should return 400 if batch data is missing")
    void testCreateBatchMissingData() throws Exception {
        // Given
        EODAccrualBatchController.CreateBatchRequest request = new EODAccrualBatchController.CreateBatchRequest();
        request.setBatch(null);

        // When/Then
        mockMvc.perform(post("/eod-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /eod-batches/{batchId} should retrieve batch by ID")
    void testGetBatchById() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(EODAccrualBatch.class), isNull())).thenReturn(testBatchWithMetadata);

        // When/Then
        mockMvc.perform(get("/eod-batches/{batchId}", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$.entity.mode").value("BACKDATED"))
                .andExpect(jsonPath("$.metadata.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("GET /eod-batches/{batchId} should return 404 if not found")
    void testGetBatchByIdNotFound() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(EODAccrualBatch.class), isNull())).thenThrow(new RuntimeException("Not found"));

        // When/Then
        mockMvc.perform(get("/eod-batches/{batchId}", testTechnicalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /eod-batches/{batchId} should update batch")
    void testUpdateBatch() throws Exception {
        // Given
        EODAccrualBatch updatedBatch = new EODAccrualBatch();
        updatedBatch.setBatchId(testBatch.getBatchId());
        updatedBatch.setAsOfDate(LocalDate.of(2025, 8, 15));
        updatedBatch.setMode(BatchMode.BACKDATED);

        BatchMetrics updatedMetrics = new BatchMetrics();
        updatedMetrics.setEligibleLoans(100);
        updatedMetrics.setProcessedLoans(50);
        updatedMetrics.setAccrualsCreated(50);
        updatedBatch.setMetrics(updatedMetrics);

        EntityMetadata updatedMetadata = new EntityMetadata();
        updatedMetadata.setId(testTechnicalId);
        updatedMetadata.setState("GENERATING");

        EntityWithMetadata<EODAccrualBatch> updatedWithMetadata =
            new EntityWithMetadata<>(updatedBatch, updatedMetadata);

        when(entityService.update(eq(testTechnicalId), any(EODAccrualBatch.class), isNull()))
            .thenReturn(updatedWithMetadata);

        EODAccrualBatchController.UpdateBatchRequest request = new EODAccrualBatchController.UpdateBatchRequest();
        request.setBatch(updatedBatch);

        // When/Then
        mockMvc.perform(patch("/eod-batches/{batchId}", testTechnicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.metrics.processedLoans").value(50))
                .andExpect(jsonPath("$.metadata.state").value("GENERATING"));
    }

    @Test
    @DisplayName("PATCH /eod-batches/{batchId} with transition should update and trigger transition")
    void testUpdateBatchWithTransition() throws Exception {
        // Given
        EODAccrualBatch canceledBatch = new EODAccrualBatch();
        canceledBatch.setBatchId(testBatch.getBatchId());
        canceledBatch.setState(EODAccrualBatchState.CANCELED);

        EntityMetadata canceledMetadata = new EntityMetadata();
        canceledMetadata.setId(testTechnicalId);
        canceledMetadata.setState("CANCELED");

        EntityWithMetadata<EODAccrualBatch> canceledWithMetadata =
            new EntityWithMetadata<>(canceledBatch, canceledMetadata);

        when(entityService.update(eq(testTechnicalId), any(EODAccrualBatch.class), eq("CANCEL")))
            .thenReturn(canceledWithMetadata);

        EODAccrualBatchController.UpdateBatchRequest request = new EODAccrualBatchController.UpdateBatchRequest();
        request.setBatch(testBatch);

        TransitionRequest transitionRequest = new TransitionRequest();
        transitionRequest.setName("CANCEL");
        transitionRequest.setComment("Canceling due to data issue");
        request.setTransitionRequest(transitionRequest);

        // When/Then
        mockMvc.perform(patch("/eod-batches/{batchId}", testTechnicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.state").value("CANCELED"));
    }

    @Test
    @DisplayName("GET /eod-batches should query batches with filters")
    void testQueryBatchesWithFilters() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);

        when(entityService.search(any(ModelSpec.class), any(GroupCondition.class),
            eq(EODAccrualBatch.class), isNull())).thenReturn(batches);

        // When/Then
        mockMvc.perform(get("/eod-batches")
                .param("asOfDate", "2025-08-15")
                .param("mode", "BACKDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$[0].entity.mode").value("BACKDATED"));
    }

    @Test
    @DisplayName("GET /eod-batches should return all batches when no filters")
    void testQueryBatchesNoFilters() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(EODAccrualBatch.class), isNull()))
            .thenReturn(batches);

        // When/Then
        mockMvc.perform(get("/eod-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity.asOfDate").value("2025-08-15"));
    }

    @Test
    @DisplayName("GET /eod-batches with state filter should filter by metadata state")
    void testQueryBatchesWithStateFilter() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(EODAccrualBatch.class), isNull()))
            .thenReturn(batches);

        // When/Then
        mockMvc.perform(get("/eod-batches")
                .param("state", "REQUESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metadata.state").value("REQUESTED"));
    }
}

