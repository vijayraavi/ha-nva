package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.azure.practices.nvadaemon.AzureClient;

public class NamedResourceId implements ConfigurationValidation {
    private String name;
    private String id;

    @JsonCreator
    public NamedResourceId(@JsonProperty("name")String name,
                           @JsonProperty("id")String id) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name),
            "name cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(id),
            "id cannot be null or empty");
        this.name = name;
        this.id = id;
    }

    public String getName() { return this.name; }

    public String getId() { return this.id; }

    public void validate(AzureClient azureClient) {
    }
}
