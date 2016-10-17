package com.microsoft.azure.practices.chain;

import java.util.concurrent.ConcurrentHashMap;

public class ContextMap
    extends ConcurrentHashMap<String, Object>
    implements Context {
    public ContextMap() {
        super();
    }

    public <T extends Object> T retrieve(String key) {
        Object valueObject = get(key);
        if (valueObject == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        T value = (T)valueObject;
        return value;
    }
}
