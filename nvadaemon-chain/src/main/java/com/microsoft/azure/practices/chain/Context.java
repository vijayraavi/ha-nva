package com.microsoft.azure.practices.chain;

import java.util.Map;

public interface Context extends Map<String, Object> {
    <T extends Object> T retrieve(String key);
}
