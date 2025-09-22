package e2e.command.service;

import e2e.CommandContext;
import e2e.entity.PrizeEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("service.updateItem")
public class UpdateItem implements e2e.TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        final var entity = commandContext.objectMapper().convertValue(args.get("entity"), PrizeEntity.class);
        return commandContext.entityService().update(
                UUID.fromString((String) args.get("entityId")),
                entity,
                (String) args.get("transitionName")
        );
    }
}
