package e2e.command.service;

import e2e.CommandContext;
import e2e.TestCommand;
import e2e.entity.PrizeEntity;
import java.util.Map;
import java.util.UUID;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.stereotype.Component;

@Component("service.getItem")
public class GetItem implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        final var modelSpec = new ModelSpec();
        modelSpec.setName((String) args.get("modelName"));
        modelSpec.setVersion((Integer) args.get("modelVersion"));
        return commandContext.entityService().getById(
                UUID.fromString((String) args.get("entityId")),
                modelSpec,
                PrizeEntity.class
        );
    }
}
