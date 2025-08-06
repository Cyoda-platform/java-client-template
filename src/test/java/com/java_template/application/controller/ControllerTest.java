package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerTest {

    @Mock
    private EntityService entityService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private Controller controller;

    private ObjectNode testHackerNewsItem;
    private UUID testTechnicalId;

    @BeforeEach
    void setUp() {
        ObjectMapper realMapper = new ObjectMapper();
        testHackerNewsItem = realMapper.createObjectNode();
        testHackerNewsItem.put("id", 8863);
        testHackerNewsItem.put("type", "story");
        testHackerNewsItem.put("by", "dhouston");
        testHackerNewsItem.put("time", 1175714200);
        testHackerNewsItem.put("text", "Example story text");

        testTechnicalId = UUID.randomUUID();
    }

    @Test
    void createHackerNewsItem_Success() throws Exception {
        // Arrange
        when(entityService.addItem(eq(HackerNewsItem.ENTITY_NAME), eq(ENTITY_VERSION), any(ObjectNode.class)))
                .thenReturn(CompletableFuture.completedFuture(testTechnicalId));

        // Act
        ResponseEntity<?> response = controller.createHackerNewsItem(testHackerNewsItem);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        assertEquals(testTechnicalId.toString(), responseBody.get("technicalId"));

        // Verify entityService was called with original JSON (no enrichment in controller)
        verify(entityService).addItem(eq(HackerNewsItem.ENTITY_NAME), eq(ENTITY_VERSION), eq(testHackerNewsItem));
    }

    @Test
    void createHackerNewsItem_EntityServiceThrowsException() throws Exception {
        // Arrange
        when(entityService.addItem(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

        // Act
        ResponseEntity<?> response = controller.createHackerNewsItem(testHackerNewsItem);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        assertEquals("Internal server error", responseBody.get("error"));
    }

    @Test
    void getHackerNewsItem_Success() throws Exception {
        // Arrange
        String technicalIdString = testTechnicalId.toString();
        ObjectMapper mapper = new ObjectMapper();

        // Create item data
        ObjectNode itemData = mapper.createObjectNode();
        itemData.put("id", 8863);
        itemData.put("type", "story");
        itemData.put("importTimestamp", "2024-04-27T15:30:00Z");

        // Create meta data
        ObjectNode metaData = mapper.createObjectNode();
        metaData.put("id", technicalIdString);
        metaData.put("state", "completed");
        metaData.put("creationDate", "2024-04-27T15:30:00Z");

        // Create full response with data and meta
        ObjectNode fullResponse = mapper.createObjectNode();
        fullResponse.set("data", itemData);
        fullResponse.set("meta", metaData);

        when(entityService.getItemWithMetaFields(eq(HackerNewsItem.ENTITY_NAME), eq(ENTITY_VERSION), eq(testTechnicalId)))
                .thenReturn(CompletableFuture.completedFuture(fullResponse));
        when(objectMapper.createObjectNode()).thenReturn(mapper.createObjectNode());

        // Act
        ResponseEntity<?> response = controller.getHackerNewsItem(technicalIdString);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // Verify response structure
        ObjectNode responseBody = (ObjectNode) response.getBody();
        assertTrue(responseBody.has("item"));
        assertTrue(responseBody.has("technicalId"));
        assertTrue(responseBody.has("state"));
        assertTrue(responseBody.has("creationDate"));

        assertEquals(technicalIdString, responseBody.get("technicalId").asText());
        assertEquals("completed", responseBody.get("state").asText());
        assertEquals("2024-04-27T15:30:00Z", responseBody.get("creationDate").asText());

        // Verify item data is separate and doesn't contain technicalId
        JsonNode itemNode = responseBody.get("item");
        assertEquals(8863, itemNode.get("id").asInt());
        assertEquals("story", itemNode.get("type").asText());
        assertFalse(itemNode.has("technicalId")); // technicalId should not be in item

        verify(entityService).getItemWithMetaFields(eq(HackerNewsItem.ENTITY_NAME), eq(ENTITY_VERSION), eq(testTechnicalId));
    }

    @Test
    void getHackerNewsItem_InvalidTechnicalIdFormat() throws Exception {
        // Act
        ResponseEntity<?> response = controller.getHackerNewsItem("invalid-uuid");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        assertEquals("Invalid technicalId format", responseBody.get("error"));

        verify(entityService, never()).getItemWithMetaFields(any(), any(), any());
    }

    @Test
    void getHackerNewsItem_NotFound() throws Exception {
        // Arrange
        String technicalIdString = testTechnicalId.toString();
        when(entityService.getItemWithMetaFields(eq(HackerNewsItem.ENTITY_NAME), eq(ENTITY_VERSION), eq(testTechnicalId)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        ResponseEntity<?> response = controller.getHackerNewsItem(technicalIdString);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        assertEquals("HackerNewsItem not found", responseBody.get("error"));
    }

    @Test
    void getHackerNewsItem_EntityServiceThrowsException() throws Exception {
        // Arrange
        String technicalIdString = testTechnicalId.toString();
        when(entityService.getItemWithMetaFields(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

        // Act
        ResponseEntity<?> response = controller.getHackerNewsItem(technicalIdString);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        assertEquals("Internal server error", responseBody.get("error"));
    }
}
