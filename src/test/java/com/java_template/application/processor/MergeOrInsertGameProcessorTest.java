package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class MergeOrInsertGameProcessorTest {

    @Test
    public void testInsertNewGameWhenNotFound() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = Mockito.mock(EntityService.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        MergeOrInsertGameProcessor processor = new MergeOrInsertGameProcessor(sf, entityService, jsonUtils);

        Game incoming = new Game();
        incoming.setGameId("g1");
        incoming.setDate("2025-03-25");
        incoming.setHomeTeam("LAL");

        // Mock getItemsByCondition to return empty array
        java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> future = CompletableFuture.completedFuture(Mockito.mock(com.fasterxml.jackson.databind.node.ArrayNode.class));
        Mockito.when(entityService.getItemsByCondition(Mockito.eq(Game.ENTITY_NAME), Mockito.eq(String.valueOf(Game.ENTITY_VERSION)), Mockito.any(), Mockito.eq(true))).thenReturn(future);

        java.lang.reflect.Method m = MergeOrInsertGameProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Game out = (Game) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, incoming));

        assertNotNull(out);
        assertEquals("g1", out.getGameId());
    }
}
