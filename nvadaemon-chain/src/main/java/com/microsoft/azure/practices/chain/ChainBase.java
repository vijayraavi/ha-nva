package com.microsoft.azure.practices.chain;

import java.util.ArrayList;
import java.util.List;

public class ChainBase<C extends Context> implements Chain<C> {
    private final List<Command<C>> commands = new ArrayList<Command<C>>();

    public void execute(C context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        for (Command<C> cmd : commands) {
            cmd.execute(context);
        }
    }

    public <Cmd extends Command<C>> void addCommand(Cmd command) {
        if (command == null) {
            throw new IllegalArgumentException();
        }

        commands.add(command);
    }
}
