package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test to verify that ProblemDetail responses are properly serialized and returned
 * with correct HTTP status codes and body content.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ProblemDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntityService entityService;

    @Test
    @DisplayName("Should return ProblemDetail with 409 CONFLICT when duplicate party exists")
    public void testDuplicatePartyReturnsProblemDetail() throws Exception {
        // Given - existing party
        Party existingParty = new Party();
        existingParty.setPartyId("PARTY-001");
        existingParty.setLegalName("Existing Party");
        
        EntityWithMetadata<Party> existingEntity = new EntityWithMetadata<>(
            existingParty,
            null
        );

        when(entityService.findByBusinessIdOrNull(
            any(ModelSpec.class),
            eq("PARTY-001"),
            eq("partyId"),
            eq(Party.class)
        )).thenReturn(existingEntity);

        // When - attempting to create duplicate
        String requestBody = """
            {
                "partyId": "PARTY-001",
                "legalName": "Duplicate Party",
                "partyType": "BORROWER",
                "jurisdiction": "US",
                "status": "ACTIVE"
            }
            """;

        // Then - should return 409 with ProblemDetail
        mockMvc.perform(post("/ui/parties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Party already exists with ID: PARTY-001"));
    }

    @Test
    @DisplayName("Should return ProblemDetail with 400 BAD_REQUEST when creation fails")
    public void testCreationFailureReturnsProblemDetail() throws Exception {
        // Given - service throws exception
        when(entityService.findByBusinessIdOrNull(
            any(ModelSpec.class),
            anyString(),
            anyString(),
            eq(Party.class)
        )).thenReturn(null);

        when(entityService.create(any(Party.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When - attempting to create party
        String requestBody = """
            {
                "partyId": "PARTY-002",
                "legalName": "New Party",
                "partyType": "BORROWER",
                "jurisdiction": "US",
                "status": "ACTIVE"
            }
            """;

        // Then - should return 400 with ProblemDetail
        mockMvc.perform(post("/ui/parties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }
}

