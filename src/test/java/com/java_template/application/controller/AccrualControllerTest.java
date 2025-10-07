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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AccrualController
 */
@WebMvcTest(controllers = AccrualController.class,
            excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@EnableSpringDataWebSupport
class AccrualControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntityService entityService;

    private ObjectMapper objectMapper;

    private Accrual testAccrual;
    private EntityWithMetadata<Accrual> testAccrualWithMetadata;
    private UUID testTechnicalId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        testTechnicalId = UUID.randomUUID();

        // Create test accrual
        testAccrual = new Accrual();
        testAccrual.setAccrualId("ACC-2025-001");
        testAccrual.setLoanId("LOAN-123");
        testAccrual.setAsOfDate(LocalDate.of(2025, 10, 7));
        testAccrual.setCurrency("USD");
        testAccrual.setDayCountConvention(DayCountConvention.ACT_360);
        testAccrual.setDayCountFraction(new BigDecimal("0.002778"));
        testAccrual.setInterestAmount(new BigDecimal("96.00"));

        // Create principal snapshot
        PrincipalSnapshot snapshot = new PrincipalSnapshot();
        snapshot.setAmount(new BigDecimal("100000.00"));
        snapshot.setEffectiveAtStartOfDay(true);
        testAccrual.setPrincipalSnapshot(snapshot);

        // Create metadata
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(testTechnicalId);
        metadata.setState("NEW");
        metadata.setCreationDate(new java.util.Date());

        testAccrualWithMetadata = new EntityWithMetadata<>(testAccrual, metadata);
    }

    @Test
    @DisplayName("POST /ui/accruals should create a new accrual")
    void testCreateAccrual() throws Exception {
        // Given
        when(entityService.findByBusinessIdOrNull(any(ModelSpec.class), eq("ACC-2025-001"),
            eq("accrualId"), eq(Accrual.class))).thenReturn(null);
        when(entityService.create(any(Accrual.class))).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(post("/ui/accruals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAccrual)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$.entity.loanId").value("LOAN-123"))
                .andExpect(jsonPath("$.meta.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("POST /ui/accruals should return 409 if accrual already exists")
    void testCreateAccrualDuplicate() throws Exception {
        // Given
        when(entityService.findByBusinessIdOrNull(any(ModelSpec.class), eq("ACC-2025-001"),
            eq("accrualId"), eq(Accrual.class))).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(post("/ui/accruals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAccrual)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /ui/accruals/{id} should retrieve accrual by ID")
    void testGetAccrualById() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(Accrual.class), isNull())).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/accruals/{id}", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$.entity.loanId").value("LOAN-123"))
                .andExpect(jsonPath("$.meta.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("GET /ui/accruals/{id}?pointInTime should retrieve accrual at point in time")
    void testGetAccrualByIdWithPointInTime() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(Accrual.class), any(Date.class))).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/accruals/{id}", testTechnicalId)
                .param("pointInTime", "2025-10-03T10:15:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"));
    }

    @Test
    @DisplayName("GET /ui/accruals/{id} should return 404 if not found")
    void testGetAccrualByIdNotFound() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(Accrual.class), any(Date.class))).thenThrow(new RuntimeException("Not found"));

        // When/Then
        mockMvc.perform(get("/ui/accruals/{id}", testTechnicalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ui/accruals/business/{accrualId} should retrieve accrual by business ID")
    void testGetAccrualByBusinessId() throws Exception {
        // Given
        when(entityService.findByBusinessId(any(ModelSpec.class), eq("ACC-2025-001"), eq("accrualId"),
            eq(Accrual.class), isNull())).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(get("/ui/accruals/business/{accrualId}", "ACC-2025-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"));
    }

    @Test
    @DisplayName("GET /ui/accruals/{id}/changes should retrieve change history")
    void testGetAccrualChangesMetadata() throws Exception {
        // Given
        List<EntityChangeMeta> changes = List.of();
        when(entityService.getEntityChangesMetadata(eq(testTechnicalId), any(Date.class)))
            .thenReturn(changes);

        // When/Then
        mockMvc.perform(get("/ui/accruals/{id}/changes", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("PUT /ui/accruals/{id} should update accrual")
    void testUpdateAccrual() throws Exception {
        // Given
        Accrual updatedAccrual = new Accrual();
        updatedAccrual.setAccrualId("ACC-2025-001");
        updatedAccrual.setInterestAmount(new BigDecimal("100.00"));

        EntityMetadata updatedMetadata = new EntityMetadata();
        updatedMetadata.setId(testTechnicalId);
        updatedMetadata.setState("CALCULATED");

        EntityWithMetadata<Accrual> updatedWithMetadata =
            new EntityWithMetadata<>(updatedAccrual, updatedMetadata);

        when(entityService.update(eq(testTechnicalId), any(Accrual.class), isNull()))
            .thenReturn(updatedWithMetadata);

        // When/Then
        mockMvc.perform(put("/ui/accruals/{id}", testTechnicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedAccrual)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.interestAmount").value(100.00))
                .andExpect(jsonPath("$.meta.state").value("CALCULATED"));
    }

    @Test
    @DisplayName("PUT /ui/accruals/{id}?transition should update and trigger transition")
    void testUpdateAccrualWithTransition() throws Exception {
        // Given
        Accrual postedAccrual = new Accrual();
        postedAccrual.setAccrualId("ACC-2025-001");

        EntityMetadata postedMetadata = new EntityMetadata();
        postedMetadata.setId(testTechnicalId);
        postedMetadata.setState("POSTED");

        EntityWithMetadata<Accrual> postedWithMetadata =
            new EntityWithMetadata<>(postedAccrual, postedMetadata);

        when(entityService.update(eq(testTechnicalId), any(Accrual.class), eq("POST")))
            .thenReturn(postedWithMetadata);

        // When/Then
        mockMvc.perform(put("/ui/accruals/{id}", testTechnicalId)
                .param("transition", "POST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAccrual)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.state").value("POSTED"));
    }

    @Test
    @DisplayName("GET /ui/accruals should list accruals with pagination when no filters")
    void testListAccrualsWithPagination() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);
        Page<EntityWithMetadata<Accrual>> page = new PageImpl<>(accruals);

        when(entityService.findAll(any(ModelSpec.class), any(Pageable.class),
            eq(Accrual.class), isNull())).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/ui/accruals")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entity.accrualId").value("ACC-2025-001"));
    }

    @Test
    @DisplayName("GET /ui/accruals with filters should query accruals")
    void testListAccrualsWithFilters() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);

        when(entityService.search(any(ModelSpec.class), any(GroupCondition.class),
            eq(Accrual.class), isNull())).thenReturn(accruals);

        // When/Then
        mockMvc.perform(get("/ui/accruals")
                .param("loanId", "LOAN-123")
                .param("asOfDate", "2025-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$[0].entity.loanId").value("LOAN-123"));
    }

    @Test
    @DisplayName("GET /ui/accruals with state filter should filter by metadata state")
    void testListAccrualsWithStateFilter() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class), isNull()))
            .thenReturn(accruals);

        // When/Then
        mockMvc.perform(get("/ui/accruals")
                .param("state", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meta.state").value("NEW"));
    }

    @Test
    @DisplayName("DELETE /ui/accruals/{id} should delete accrual")
    void testDeleteAccrual() throws Exception {
        // When/Then
        mockMvc.perform(delete("/ui/accruals/{id}", testTechnicalId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /ui/accruals/business/{accrualId} should delete accrual by business ID")
    void testDeleteAccrualByBusinessId() throws Exception {
        // Given
        when(entityService.deleteByBusinessId(any(ModelSpec.class), eq("ACC-2025-001"), eq("accrualId"),
            eq(Accrual.class))).thenReturn(true);

        // When/Then
        mockMvc.perform(delete("/ui/accruals/business/{accrualId}", "ACC-2025-001"))
                .andExpect(status().isNoContent());
    }
}

