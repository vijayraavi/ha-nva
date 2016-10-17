package com.microsoft.azure.practices.chain;

public interface Command<C extends Context> {
    void execute(C context) throws Exception;
}
