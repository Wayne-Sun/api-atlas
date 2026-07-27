package com.api.atlas.controller;

import com.api.atlas.config.DataSourceFactoryRegistry;
import com.api.atlas.model.DataSource;
import com.api.atlas.model.DataSourceCreateDTO;
import com.api.atlas.model.DataSourceUpdateDTO;
import com.api.atlas.model.R;
import com.api.atlas.service.DataSourceService;
import com.github.pagehelper.PageInfo;
import com.zaxxer.hikari.HikariDataSource;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final DataSourceFactoryRegistry factoryRegistry;

    public DataSourceController(DataSourceService dataSourceService, DataSourceFactoryRegistry factoryRegistry) {
        this.dataSourceService = dataSourceService;
        this.factoryRegistry = factoryRegistry;
    }

    public static class TestConnectionRequest {
        @NotBlank
        private String type;
        @NotBlank
        private String host;
        @NotNull
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        private String apiKey;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    @PostMapping
    public R<DataSource> create(@Valid @RequestBody DataSourceCreateDTO dto) {
        DataSource dataSource = dataSourceService.create(dto);
        return R.created(dataSource);
    }

    @GetMapping
    public R<List<DataSource>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @Valid @RequestParam(defaultValue = "10") @Max(1000) int pageSize) {
        PageInfo<DataSource> page = dataSourceService.list(name, type, status, pageNum, pageSize);
        return R.ok(page.getList(), page);
    }

    @GetMapping("/{id}")
    public R<DataSource> getById(@PathVariable Long id) {
        return R.ok(dataSourceService.getById(id));
    }

    @PutMapping("/{id}")
    public R<DataSource> update(@PathVariable Long id, @Valid @RequestBody DataSourceUpdateDTO dto) {
        return R.ok(dataSourceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return R.deleted();
    }

    @PatchMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dataSourceService.updateStatus(id, body.get("status"));
        return R.ok(null);
    }

    @PostMapping("/test-connection")
    public R<Map<String, Object>> testConnection(@Valid @RequestBody TestConnectionRequest req) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            if ("Elasticsearch".equals(req.getType())) {
                ElasticsearchClient client = (ElasticsearchClient) factoryRegistry.getFactory("Elasticsearch")
                        .createClient(req.getHost(), req.getPort(), req.getDatabaseName(),
                                req.getUsername(), req.getPassword(), req.getApiKey());
                try {
                    client.cluster().health();
                } finally {
                    client._transport().close();
                }
            } else {
                javax.sql.DataSource ds = (javax.sql.DataSource) factoryRegistry.getFactory(req.getType())
                        .createClient(req.getHost(), req.getPort(),
                                req.getDatabaseName() != null ? req.getDatabaseName() : "",
                                req.getUsername(), req.getPassword(), null);
                try (Connection conn = ds.getConnection()) {
                    // Connection obtained successfully
                } finally {
                    if (ds instanceof HikariDataSource hikariDs) {
                        hikariDs.close();
                    }
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            result.put("connected", true);
            result.put("responseTime", (int) elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            result.put("connected", false);
            result.put("responseTime", (int) elapsed);
            result.put("error", e.getMessage());
        }
        return R.ok(result);
    }
}
