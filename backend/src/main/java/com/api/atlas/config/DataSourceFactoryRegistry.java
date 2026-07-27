package com.api.atlas.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceFactoryRegistry {

    private final Map<String, DataSourceFactory<?>> factoryMap = new ConcurrentHashMap<>();

    public DataSourceFactoryRegistry(List<DataSourceFactory<?>> factories) {
        factories.forEach(f -> factoryMap.put(f.getType(), f));
    }

    @SuppressWarnings("unchecked")
    public <T> DataSourceFactory<T> getFactory(String type) {
        DataSourceFactory<?> factory = factoryMap.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported datasource type: " + type);
        }
        return (DataSourceFactory<T>) factory;
    }
}
