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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AccrualController
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccrualControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EntityService entityService;

    private Accrual testAccrual;
    private EntityWithMetadata<Accrual> testAccrualWithMetadata;
    private UUID testTechnicalId;

    @BeforeEach
    void setUp() {
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
        testAccrual.setState(AccrualState.NEW);

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
    @DisplayName("POST /accruals should create a new accrual")
    void testCreateAccrual() throws Exception {
        // Given
        when(entityService.findByBusinessIdOrNull(any(ModelSpec.class), eq("ACC-2025-001"),
            eq("accrualId"), eq(Accrual.class))).thenReturn(null);
        when(entityService.create(any(Accrual.class))).thenReturn(testAccrualWithMetadata);

        AccrualController.CreateAccrualRequest request = new AccrualController.CreateAccrualRequest();
        request.setAccrual(testAccrual);

        // When/Then
        mockMvc.perform(post("/accruals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$.entity.loanId").value("LOAN-123"))
                .andExpect(jsonPath("$.metadata.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("POST /accruals with transition should create and trigger transition")
    void testCreateAccrualWithTransition() throws Exception {
        // Given
        EntityMetadata calculatedMetadata = new EntityMetadata();
        calculatedMetadata.setId(testTechnicalId);
        calculatedMetadata.setState("CALCULATED");
        calculatedMetadata.setCreationDate(new java.util.Date());

        Accrual calculatedAccrual = new Accrual();
        calculatedAccrual.setAccrualId("ACC-2025-001");
        calculatedAccrual.setLoanId("LOAN-123");
        calculatedAccrual.setState(AccrualState.CALCULATED);

        EntityWithMetadata<Accrual> calculatedWithMetadata =
            new EntityWithMetadata<>(calculatedAccrual, calculatedMetadata);

        when(entityService.findByBusinessIdOrNull(any(ModelSpec.class), eq("ACC-2025-001"),
            eq("accrualId"), eq(Accrual.class))).thenReturn(null);
        when(entityService.create(any(Accrual.class))).thenReturn(testAccrualWithMetadata);
        when(entityService.update(eq(testTechnicalId), any(Accrual.class), eq("CALCULATE")))
            .thenReturn(calculatedWithMetadata);

        AccrualController.CreateAccrualRequest request = new AccrualController.CreateAccrualRequest();
        request.setAccrual(testAccrual);

        TransitionRequest transitionRequest = new TransitionRequest();
        transitionRequest.setName("CALCULATE");
        transitionRequest.setComment("Auto-calculate interest");
        request.setTransitionRequest(transitionRequest);

        EngineOptions engineOptions = new EngineOptions();
        engineOptions.setSimulate(false);
        engineOptions.setMaxSteps(50);
        request.setEngineOptions(engineOptions);

        // When/Then
        mockMvc.perform(post("/accruals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.state").value("CALCULATED"));
    }

    @Test
    @DisplayName("POST /accruals should return 409 if accrual already exists")
    void testCreateAccrualDuplicate() throws Exception {
        // Given
        when(entityService.findByBusinessIdOrNull(any(ModelSpec.class), eq("ACC-2025-001"),
            eq("accrualId"), eq(Accrual.class))).thenReturn(testAccrualWithMetadata);

        AccrualController.CreateAccrualRequest request = new AccrualController.CreateAccrualRequest();
        request.setAccrual(testAccrual);

        // When/Then
        mockMvc.perform(post("/accruals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /accruals/{accrualId} should retrieve accrual by ID")
    void testGetAccrualById() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(Accrual.class), isNull())).thenReturn(testAccrualWithMetadata);

        // When/Then
        mockMvc.perform(get("/accruals/{accrualId}", testTechnicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$.entity.loanId").value("LOAN-123"))
                .andExpect(jsonPath("$.metadata.id").value(testTechnicalId.toString()));
    }

    @Test
    @DisplayName("GET /accruals/{accrualId} should return 404 if not found")
    void testGetAccrualByIdNotFound() throws Exception {
        // Given
        when(entityService.getById(eq(testTechnicalId), any(ModelSpec.class),
            eq(Accrual.class), isNull())).thenThrow(new RuntimeException("Not found"));

        // When/Then
        mockMvc.perform(get("/accruals/{accrualId}", testTechnicalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /accruals/{accrualId} should update accrual")
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

        AccrualController.UpdateAccrualRequest request = new AccrualController.UpdateAccrualRequest();
        request.setAccrual(updatedAccrual);

        // When/Then
        mockMvc.perform(patch("/accruals/{accrualId}", testTechnicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.interestAmount").value(100.00))
                .andExpect(jsonPath("$.metadata.state").value("CALCULATED"));
    }

    @Test
    @DisplayName("GET /accruals should query accruals with filters")
    void testQueryAccrualsWithFilters() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);

        when(entityService.search(any(ModelSpec.class), any(GroupCondition.class),
            eq(Accrual.class), isNull())).thenReturn(accruals);

        // When/Then
        mockMvc.perform(get("/accruals")
                .param("loanId", "LOAN-123")
                .param("asOfDate", "2025-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity.accrualId").value("ACC-2025-001"))
                .andExpect(jsonPath("$[0].entity.loanId").value("LOAN-123"));
    }

    @Test
    @DisplayName("GET /accruals should return all accruals when no filters")
    void testQueryAccrualsNoFilters() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class), isNull()))
            .thenReturn(accruals);

        // When/Then
        mockMvc.perform(get("/accruals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity.accrualId").value("ACC-2025-001"));
    }

    @Test
    @DisplayName("GET /accruals with state filter should filter by metadata state")
    void testQueryAccrualsWithStateFilter() throws Exception {
        // Given
        List<EntityWithMetadata<Accrual>> accruals = List.of(testAccrualWithMetadata);

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class), isNull()))
            .thenReturn(accruals);

        // When/Then
        mockMvc.perform(get("/accruals")
                .param("state", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metadata.state").value("NEW"));
    }
}

