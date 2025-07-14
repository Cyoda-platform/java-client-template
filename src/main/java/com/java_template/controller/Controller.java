import java.util.UUID;
import com.java_template.common.service.EntityService;
import java.util.concurrent.CompletableFuture;

public class MyService {
    private final EntityService entityService;

    public MyService(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<UUID> createEntity(Data data) {
        return entityService.addItem(
            entityModel = "example",
            entityVersion = ENTITY_VERSION,
            entity = data
        );
    }
}