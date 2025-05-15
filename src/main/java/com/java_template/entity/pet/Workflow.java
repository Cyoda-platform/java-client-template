import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processPet(ObjectNode petNode) {
        return processAddTimestamp(petNode)
            .thenCompose(this::processAddFullDescription)
            .thenCompose(this::processAsyncLogging);
    }

    private CompletableFuture<ObjectNode> processAddTimestamp(ObjectNode petNode) {
        petNode.put("lastModified", System.currentTimeMillis());
        return CompletableFuture.completedFuture(petNode);
    }

    private CompletableFuture<ObjectNode> processAddFullDescription(ObjectNode petNode) {
        String name = petNode.path("name").asText("");
        String category = petNode.path("category").asText("");
        String status = petNode.path("status").asText("");
        petNode.put("fullDescription", name + " (" + category + ") - " + status);
        return CompletableFuture.completedFuture(petNode);
    }

    private CompletableFuture<ObjectNode> processAsyncLogging(ObjectNode petNode) {
        CompletableFuture.runAsync(() -> {
            logger.info("Async pre-persistence notification for pet: {}", petNode.toString());
            // Potentially notify external system here
        });
        return CompletableFuture.completedFuture(petNode);
    }
}