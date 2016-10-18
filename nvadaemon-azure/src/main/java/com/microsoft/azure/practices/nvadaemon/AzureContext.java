package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.Azure;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.practices.chain.ContextMap;
import com.microsoft.rest.credentials.ServiceClientCredentials;

import java.io.IOException;

public class AzureContext extends ContextMap {
    private static final String AZURE_KEY = "Azure";

    private AzureContext() {
    }

    private void setAzure(Azure azure) {
        Preconditions.checkNotNull(azure);
        put(AZURE_KEY, azure);
    }

    public Azure getAzure() {
        return retrieve(AZURE_KEY);
    }

    public static AzureContext create() throws CloudException, IOException {
        AzureContext context = new AzureContext();
        String clientId = "d5e26522-ad01-4a89-96e2-ee7033b780ae";
        String tenantId = "72f988bf-86f1-41af-91ab-2d7cd011db47";
        String secret = "NPGMmWXcN0GaPQAbA59j/wniRdvH78ob5lgf616TJeg=";
        ServiceClientCredentials credentials = new ApplicationTokenCredentials(
                clientId, tenantId, secret, AzureEnvironment.AZURE);

        Azure azure = Azure
                .configure()
                .authenticate(credentials)
                .withDefaultSubscription();
        context.setAzure(azure);
        return context;
    }
}
