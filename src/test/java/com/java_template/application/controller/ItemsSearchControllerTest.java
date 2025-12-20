package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.service.ItemSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ItemsSearchController
 * Tests REST endpoint validation, parameter handling, and response mapping
 */
@DisplayName("ItemsSearchController Tests")
class ItemsSearchControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ItemSearchService itemSearchService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        ItemsSearchController controller = new ItemsSearchController(itemSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    @DisplayName("Should return 400 when query parameter is missing")
    void testSearchMissingQuery() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new SearchRequest(null, null, null, null));
        mockMvc.perform(post("/items/search")
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(itemSearchService, never()).search(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should return 400 when query parameter is empty")
    void testSearchEmptyQuery() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new SearchRequest("", null, null, null));
        mockMvc.perform(post("/items/search")
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(itemSearchService, never()).search(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should return 200 with results when search succeeds")
    void testSearchSuccess() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"results\": []}");
        when(itemSearchService.search("test", null, 20, 0))
                .thenReturn(Mono.just(mockResponse));

        String requestBody = objectMapper.writeValueAsString(new SearchRequest("test", null, null, null));
        mockMvc.perform(post("/items/search")
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());

        verify(itemSearchService).search("test", null, 20, 0);
    }

    @Test
    @DisplayName("Should use default limit and offset when not provided")
    void testSearchWithDefaults() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"results\": []}");
        when(itemSearchService.search("test", null, 20, 0))
                .thenReturn(Mono.just(mockResponse));

        mockMvc.perform(get("/items/search").param("q", "test"))
                .andExpect(status().isOk());

        verify(itemSearchService).search("test", null, 20, 0);
    }

    @Test
    @DisplayName("Should pass custom limit and offset to service")
    void testSearchWithCustomLimitAndOffset() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"results\": []}");
        when(itemSearchService.search("test", null, 50, 10))
                .thenReturn(Mono.just(mockResponse));

        mockMvc.perform(get("/items/search")
                .param("q", "test")
                .param("limit", "50")
                .param("offset", "10"))
                .andExpect(status().isOk());

        verify(itemSearchService).search("test", null, 50, 10);
    }

    @Test
    @DisplayName("Should pass type parameter to service when provided")
    void testSearchWithType() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"results\": []}");
        when(itemSearchService.search("test", "story", 20, 0))
                .thenReturn(Mono.just(mockResponse));

        mockMvc.perform(get("/items/search")
                .param("q", "test")
                .param("type", "story"))
                .andExpect(status().isOk());

        verify(itemSearchService).search("test", "story", 20, 0);
    }

    @Test
    @DisplayName("Should return 500 when service throws exception")
    void testSearchServiceError() throws Exception {
        when(itemSearchService.search(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        mockMvc.perform(get("/items/search").param("q", "test"))
                .andExpect(status().isInternalServerError());
    }
}

