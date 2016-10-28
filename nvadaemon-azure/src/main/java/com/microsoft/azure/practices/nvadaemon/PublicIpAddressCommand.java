package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.practices.chain.Command;

public class PublicIpAddressCommand implements Command<AzureContext> {
    public void execute(AzureContext context) throws Exception {
        Azure azure = context.getAzure();

    }
}
