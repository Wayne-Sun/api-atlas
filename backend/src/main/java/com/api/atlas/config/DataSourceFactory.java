package com.api.atlas.config;

public interface DataSourceFactory<T> {
    T createClient(String host, int port, String databaseName, String username, String password, String apiKey) throws Exception;

    void destroyClient(T client);

    String getType();
}
