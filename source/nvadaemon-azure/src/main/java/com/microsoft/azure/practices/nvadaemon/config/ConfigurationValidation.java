package com.microsoft.azure.practices.nvadaemon.config;

import com.microsoft.azure.practices.nvadaemon.AzureClient;

public interface ConfigurationValidation {
    void validate(AzureClient azureClient) throws ConfigurationException;
}
