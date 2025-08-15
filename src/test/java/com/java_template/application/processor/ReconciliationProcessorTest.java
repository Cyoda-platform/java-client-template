package com.java_template.application.processor;

import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class ReconciliationProcessorTest {

    @Test
    public void testReconciliationDetectsChange() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = Mockito.mock(EntityService.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        ReconciliationProcessor processor = new ReconciliationProcessor(sf, entityService, jsonUtils);

        Game incoming = new Game();
        incoming.setGameId("g1");
        incoming.setDate("2025-03-25");
        incoming.setHomeScore(100);
        incoming.setAwayScore(90);
        incoming.setStatus("final");

        // Mock existing game with different scores
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = Mockito.mock(com.fasterxml.jackson.databind.node.ArrayNode.class);
        com.fasterxml.jackson.databind.node.ObjectNode existing = Mockito.mock(com.fasterxml.jackson.databind.node.ObjectNode.class);
        Mockito.when(arrayNode.size()).thenReturn(1);
        Mockito.when(arrayNode.get(0)).thenReturn(existing);
        Mockito.when(entityService.getItemsByCondition(Mockito.eq(Game.ENTITY_NAME), Mockito.eq(String.valueOf(Game.ENTITY_VERSION)), Mockito.any(), Mockito.eq(true))).thenReturn(CompletableFuture.completedFuture(arrayNode));
        Game current = new Game();
        current.setGameId("g1");
        current.setDate("2025-03-25");
        current.setHomeScore(95);
        current.setAwayScore(90);
        current.setStatus("final");
        Mockito.when(jsonUtils.fromJsonNode(existing, Game.class)).thenReturn(current);

        java.lang.reflect.Method m = ReconciliationProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Game out = (Game) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, incoming));

        assertNotNull(out);
        assertEquals(100, out.getHomeScore());
    }
}
