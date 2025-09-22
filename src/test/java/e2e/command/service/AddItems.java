package e2e.command.service;

import com.fasterxml.jackson.core.type.TypeReference;
import e2e.CommandContext;
import e2e.TestCommand;
import e2e.entity.PrizeEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("service.addItems")
public class AddItems implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        final var entities = commandContext.objectMapper().convertValue(
                args.get("entities"),
                new TypeReference<List<PrizeEntity>>() {}
        );
        return commandContext.entityService().save(entities);
    }
}
