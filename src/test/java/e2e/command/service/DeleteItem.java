package e2e.command.service;

import e2e.CommandContext;
import e2e.TestCommand;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("service.deleteItem")
public class DeleteItem implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        return commandContext.entityService().deleteById(UUID.fromString((String) args.get("entityId")));
    }
}
