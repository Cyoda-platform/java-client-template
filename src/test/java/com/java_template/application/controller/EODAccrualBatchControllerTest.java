package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EODAccrualBatchController
 */
@WebMvcTest(controllers = EODAccrualBatchController.class,
            excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@EnableSpringDataWebSupport
class EODAccrualBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntityService entityService;

    private ObjectMapper objectMapper;

    private EODAccrualBatch testBatch;
    private EntityWithMetadata<EODAccrualBatch> testBatchWithMetadata;
    private UUID testTechnicalId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        testTechnicalId = UUID.randomUUID();

        // Create test batch
        testBatch = new EODAccrualBatch();
        testBatch.setBatchId(UUID.randomUUID());
        testBatch.setAsOfDate(LocalDate.of(2025, 8, 15));
        testBatch.setMode(BatchMode.BACKDATED);
        testBatch.setInitiatedBy("user@example.com");
        testBatch.setReasonCode("DATA_CORRECTION");

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
    @DisplayName("POST /ui/eod-batches should create a new batch")
    void testCreateBatch() throws Exception {
        // Given
        when(entityService.create(any(EODAccrualBatch.class))).thenReturn(testBatchWithMetadata);
        when(entityService.getById(any(), any(), eq(EODAccrualBatch.class))).thenReturn(testBatchWithMetadata);

        // When/Then
        mockMvc.perform(post("/ui/eod-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testBatch)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$.entity.mode").value("BACKDATED"))
                .andExpect(jsonPath("$.entity.reasonCode").value("DATA_CORRECTION"))
                .andExpect(jsonPath("$.meta.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("GET /ui/eod-batches/{id} should retrieve batch by ID")
    void testGetBatchById() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(EODAccrualBatch.class), isNull())).thenReturn(testBatchWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches/{id}", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$.entity.mode").value("BACKDATED"))
                .andExpect(jsonPath("$.meta.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("GET /ui/eod-batches/{id}?pointInTime should retrieve batch at point in time")
    void testGetBatchByIdWithPointInTime() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(EODAccrualBatch.class), any(Date.class))).thenReturn(testBatchWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches/{id}", testTechnicalId)
                .param("pointInTime", "2025-10-03T10:15:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"));
    }

    @Test
    @DisplayName("GET /ui/eod-batches/{id} should return 404 if not found")
    void testGetBatchByIdNotFound() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(EODAccrualBatch.class), any(Date.class))).thenThrow(new RuntimeException("Not found"));

        // When/Then
        mockMvc.perform(get("/ui/eod-batches/{id}", testTechnicalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ui/eod-batches/business/{batchId} should retrieve batch by business ID")
    void testGetBatchByBusinessId() throws Exception {
        // Given
        when(entityService.findByBusinessId(any(ModelSpec.class), anyString(), eq("batchId"),
            eq(EODAccrualBatch.class), isNull())).thenReturn(testBatchWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches/business/{batchId}", testBatch.getBatchId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.asOfDate").value("2025-08-15"));
    }

    @Test
    @DisplayName("GET /ui/eod-batches/{id}/changes should retrieve change history")
    void testGetBatchChangesMetadata() throws Exception {
        // Given
        List<EntityChangeMeta> changes = List.of();
        when(entityService.getEntityChangesMetadata(eq(testTechnicalId), isNull()))
            .thenReturn(changes);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches/{id}/changes", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("PUT /ui/eod-batches/{id} should update batch")
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

        // When/Then
        mockMvc.perform(put("/ui/eod-batches/{id}", testTechnicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.metrics.processedLoans").value(50))
                .andExpect(jsonPath("$.meta.state").value("GENERATING"));
    }

    @Test
    @DisplayName("PUT /ui/eod-batches/{id}?transition should update and trigger transition")
    void testUpdateBatchWithTransition() throws Exception {
        // Given
        EODAccrualBatch canceledBatch = new EODAccrualBatch();
        canceledBatch.setBatchId(testBatch.getBatchId());

        EntityMetadata canceledMetadata = new EntityMetadata();
        canceledMetadata.setId(testTechnicalId);
        canceledMetadata.setState("CANCELED");

        EntityWithMetadata<EODAccrualBatch> canceledWithMetadata =
            new EntityWithMetadata<>(canceledBatch, canceledMetadata);

        when(entityService.update(eq(testTechnicalId), any(EODAccrualBatch.class), eq("CANCEL")))
            .thenReturn(canceledWithMetadata);

        // When/Then
        mockMvc.perform(put("/ui/eod-batches/{id}", testTechnicalId)
                .param("transition", "CANCEL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.state").value("CANCELED"));
    }

    @Test
    @DisplayName("GET /ui/eod-batches should list batches with pagination when no filters")
    void testListBatchesWithPagination() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);
        Page<EntityWithMetadata<EODAccrualBatch>> page = new PageImpl<>(batches);

        when(entityService.findAll(any(ModelSpec.class), any(Pageable.class),
            eq(EODAccrualBatch.class), isNull())).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entity.asOfDate").value("2025-08-15"));
    }

    @Test
    @DisplayName("GET /ui/eod-batches with filters should query batches")
    void testListBatchesWithFilters() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);

        when(entityService.search(any(ModelSpec.class), any(GroupCondition.class),
            eq(EODAccrualBatch.class), isNull())).thenReturn(batches);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches")
                .param("asOfDate", "2025-08-15")
                .param("mode", "BACKDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entity.asOfDate").value("2025-08-15"))
                .andExpect(jsonPath("$.content[0].entity.mode").value("BACKDATED"));
    }

    @Test
    @DisplayName("GET /ui/eod-batches with state filter should filter by metadata state")
    void testListBatchesWithStateFilter() throws Exception {
        // Given
        List<EntityWithMetadata<EODAccrualBatch>> batches = List.of(testBatchWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(EODAccrualBatch.class), isNull()))
            .thenReturn(batches);

        // When/Then
        mockMvc.perform(get("/ui/eod-batches")
                .param("state", "REQUESTED"))
                .andExpect(status().isOk())
                .andDo(it -> System.out.println(it.getResponse().getContentAsString()))
                .andExpect(jsonPath("$.content[0].meta.state").value("REQUESTED"));
    }

    @Test
    @DisplayName("DELETE /ui/eod-batches/{id} should delete batch")
    void testDeleteBatch() throws Exception {
        // When/Then
        mockMvc.perform(delete("/ui/eod-batches/{id}", testTechnicalId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /ui/eod-batches/business/{batchId} should delete batch by business ID")
    void testDeleteBatchByBusinessId() throws Exception {
        // Given
        when(entityService.deleteByBusinessId(any(ModelSpec.class), anyString(), eq("batchId"),
            eq(EODAccrualBatch.class))).thenReturn(true);

        // When/Then
        mockMvc.perform(delete("/ui/eod-batches/business/{batchId}", testBatch.getBatchId()))
                .andExpect(status().isNoContent());
    }
}

