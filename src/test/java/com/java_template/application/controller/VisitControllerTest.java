package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.visit.version_1.Visit;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for VisitController
 */
@ExtendWith(MockitoExtension.class)
class VisitControllerTest {

    @Mock
    private EntityService entityService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VisitController visitController;

    private Visit testVisit;
    private EntityWithMetadata<Visit> testEntityWithMetadata;
    private UUID testEntityId;

    @BeforeEach
    void setUp() {
        testEntityId = UUID.randomUUID();
        
        testVisit = new Visit();
        testVisit.setVisitId("VISIT-001");
        testVisit.setSubjectId("SUBJ-001");
        testVisit.setStudyId("STUDY-001");
        testVisit.setVisitCode("V1");
        testVisit.setStatus("planned");
        testVisit.setPlannedDate(LocalDate.now().plusDays(7));
        testVisit.setLocked(false);
        testVisit.setWindowMinusDays(3);
        testVisit.setWindowPlusDays(3);
        testVisit.setCreatedAt(LocalDateTime.now());
        testVisit.setUpdatedAt(LocalDateTime.now());

        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(testEntityId);
        metadata.setState("VALIDATED");
        metadata.setCreationDate(new java.util.Date());

        testEntityWithMetadata = new EntityWithMetadata<>(testVisit, metadata);
    }

    @Test
    @DisplayName("createVisit should successfully create a new visit")
    void testCreateVisitSuccess() {
        // Given
        when(entityService.create(any(Visit.class))).thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.createVisit(testVisit);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testVisit.getVisitId(), response.getBody().entity().getVisitId());
        verify(entityService).create(any(Visit.class));
    }

    @Test
    @DisplayName("createVisit should handle service exceptions")
    void testCreateVisitException() {
        // Given
        when(entityService.create(any(Visit.class))).thenThrow(new RuntimeException("Service error"));

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.createVisit(testVisit);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("getVisitById should successfully retrieve visit")
    void testGetVisitByIdSuccess() {
        // Given
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.getVisitById(testEntityId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testVisit.getVisitId(), response.getBody().entity().getVisitId());
    }

    @Test
    @DisplayName("getVisitById should handle not found")
    void testGetVisitByIdNotFound() {
        // Given
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenThrow(new RuntimeException("Not found"));

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.getVisitById(testEntityId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("updateVisit should successfully update visit")
    void testUpdateVisitSuccess() {
        // Given
        when(entityService.update(eq(testEntityId), any(Visit.class), isNull()))
                .thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.updateVisit(
                testEntityId, testVisit, null);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(entityService).update(eq(testEntityId), any(Visit.class), isNull());
    }

    @Test
    @DisplayName("deleteVisit should successfully delete visit")
    void testDeleteVisitSuccess() {
        // Given
        when(entityService.deleteById(eq(testEntityId))).thenReturn(testEntityId);

        // When
        ResponseEntity<Void> response = visitController.deleteVisit(testEntityId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(entityService).deleteById(eq(testEntityId));
    }

    @Test
    @DisplayName("getAllVisits should return list of visits")
    void testGetAllVisitsSuccess() {
        // Given
        List<EntityWithMetadata<Visit>> visits = List.of(testEntityWithMetadata);
        when(entityService.findAll(any(), eq(Visit.class))).thenReturn(visits);

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = visitController.getAllVisits();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("getVisitsBySubject should return visits for subject")
    void testGetVisitsBySubjectSuccess() {
        // Given
        List<EntityWithMetadata<Visit>> visits = List.of(testEntityWithMetadata);
        when(entityService.search(any(), any(), eq(Visit.class))).thenReturn(visits);
        when(objectMapper.valueToTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = 
                visitController.getVisitsBySubject("SUBJ-001");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("getVisitsByStudy should return visits for study")
    void testGetVisitsByStudySuccess() {
        // Given
        List<EntityWithMetadata<Visit>> visits = List.of(testEntityWithMetadata);
        when(entityService.search(any(), any(), eq(Visit.class))).thenReturn(visits);
        when(objectMapper.valueToTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = 
                visitController.getVisitsByStudy("STUDY-001");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("searchVisitsByStatus should return filtered visits")
    void testSearchVisitsByStatusSuccess() {
        // Given
        List<EntityWithMetadata<Visit>> visits = List.of(testEntityWithMetadata);
        when(entityService.search(any(), any(), eq(Visit.class))).thenReturn(visits);
        when(objectMapper.valueToTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = 
                visitController.searchVisitsByStatus("planned");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("completeVisit should successfully complete visit")
    void testCompleteVisitSuccess() {
        // Given
        testVisit.setActualDate(LocalDate.now());
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);
        when(entityService.update(eq(testEntityId), any(Visit.class), eq("complete_visit")))
                .thenReturn(testEntityWithMetadata);

        VisitController.VisitCompletionRequest completionRequest = 
                new VisitController.VisitCompletionRequest();
        completionRequest.setActualDate(LocalDate.now());
        completionRequest.setCompletedBy("test-user");

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = 
                visitController.completeVisit(testEntityId, completionRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(entityService).update(eq(testEntityId), any(Visit.class), eq("complete_visit"));
    }

    @Test
    @DisplayName("completeVisit should fail for locked visit")
    void testCompleteVisitLocked() {
        // Given
        testVisit.setLocked(true);
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);

        VisitController.VisitCompletionRequest completionRequest = 
                new VisitController.VisitCompletionRequest();
        completionRequest.setActualDate(LocalDate.now());

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = 
                visitController.completeVisit(testEntityId, completionRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(entityService, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("scheduleVisit should successfully schedule visit")
    void testScheduleVisitSuccess() {
        // Given
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);
        when(entityService.update(eq(testEntityId), any(Visit.class), eq("schedule_visit")))
                .thenReturn(testEntityWithMetadata);

        VisitController.VisitSchedulingRequest schedulingRequest = 
                new VisitController.VisitSchedulingRequest();
        schedulingRequest.setPlannedDate(LocalDate.now().plusDays(14));
        schedulingRequest.setWindowMinusDays(5);
        schedulingRequest.setWindowPlusDays(5);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = 
                visitController.scheduleVisit(testEntityId, schedulingRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(entityService).update(eq(testEntityId), any(Visit.class), eq("schedule_visit"));
    }

    @Test
    @DisplayName("lockVisit should successfully lock completed visit")
    void testLockVisitSuccess() {
        // Given
        testVisit.setStatus("completed");
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);
        when(entityService.update(eq(testEntityId), any(Visit.class), isNull()))
                .thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.lockVisit(testEntityId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(entityService).update(eq(testEntityId), any(Visit.class), isNull());
    }

    @Test
    @DisplayName("lockVisit should fail for non-completed visit")
    void testLockVisitNotCompleted() {
        // Given
        testVisit.setStatus("planned");
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.lockVisit(testEntityId);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(entityService, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("unlockVisit should successfully unlock visit")
    void testUnlockVisitSuccess() {
        // Given
        testVisit.setLocked(true);
        when(entityService.getById(eq(testEntityId), any(), eq(Visit.class)))
                .thenReturn(testEntityWithMetadata);
        when(entityService.update(eq(testEntityId), any(Visit.class), isNull()))
                .thenReturn(testEntityWithMetadata);

        // When
        ResponseEntity<EntityWithMetadata<Visit>> response = visitController.unlockVisit(testEntityId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(entityService).update(eq(testEntityId), any(Visit.class), isNull());
    }

    @Test
    @DisplayName("advancedSearch should handle complex search criteria")
    void testAdvancedSearchSuccess() {
        // Given
        List<EntityWithMetadata<Visit>> visits = List.of(testEntityWithMetadata);
        when(entityService.search(any(), any(), eq(Visit.class))).thenReturn(visits);
        when(objectMapper.valueToTree(any())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        VisitController.VisitSearchRequest searchRequest = new VisitController.VisitSearchRequest();
        searchRequest.setStudyId("STUDY-001");
        searchRequest.setSubjectId("SUBJ-001");
        searchRequest.setStatus("planned");
        searchRequest.setPlannedDateFrom(LocalDate.now());
        searchRequest.setPlannedDateTo(LocalDate.now().plusDays(30));

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = 
                visitController.advancedSearch(searchRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("advancedSearch should handle empty search criteria")
    void testAdvancedSearchEmptyCriteria() {
        // Given
        VisitController.VisitSearchRequest searchRequest = new VisitController.VisitSearchRequest();

        // When
        ResponseEntity<List<EntityWithMetadata<Visit>>> response = 
                visitController.advancedSearch(searchRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(entityService, never()).search(any(), any(), any());
    }
}
