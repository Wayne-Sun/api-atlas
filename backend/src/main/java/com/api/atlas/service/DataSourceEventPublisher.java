package com.api.atlas.service;

public interface DataSourceEventPublisher {
    void onDataSourceDisabled(Long datasourceId, String datasourceName);
}
