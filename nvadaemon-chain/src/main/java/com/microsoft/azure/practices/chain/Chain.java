package com.microsoft.azure.practices.chain;

public interface Chain<C extends Context> {
    void execute(C context) throws Exception;
    <Cmd extends Command<C>> void addCommand(Cmd command);
}
