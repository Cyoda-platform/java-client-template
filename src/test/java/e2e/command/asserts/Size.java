package e2e.command.asserts;

import e2e.CommandContext;
import e2e.TestCommand;
import java.util.Collection;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;

@Component("assert.size")
public class Size implements TestCommand {
    @Override
    public Object execute(final java.util.Map<?, ?> args, final CommandContext commandContext) {
        Assertions.assertEquals(args.get("expected"), ((Collection<?>) args.get("actual")).size());
        return null;
    }
}
