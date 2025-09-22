package e2e.command.service;

import e2e.CommandContext;
import e2e.TestCommand;
import e2e.entity.PrizeEntity;
import java.util.Map;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.stereotype.Component;

@Component("service.getItems")
public class GetItems implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        final var modelSpec = new ModelSpec();
        modelSpec.setName((String) args.get("modelName"));
        modelSpec.setVersion((Integer) args.get("modelVersion"));

        return commandContext.entityService().findAll(
                modelSpec,
                PrizeEntity.class
        );
    }
}
