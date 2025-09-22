package e2e.command.service;

import e2e.CommandContext;
import e2e.TestCommand;
import e2e.entity.PrizeEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("service.addItem")
public class AddItem implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        final var entity = commandContext.objectMapper().convertValue(args.get("entity"), PrizeEntity.class);
        final var entities = List.of(entity);
        return commandContext.entityService().save(entities).getFirst();
    }
}
