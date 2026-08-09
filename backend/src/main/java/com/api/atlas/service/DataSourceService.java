package com.api.atlas.service;

import com.api.atlas.config.EncryptionUtil;
import com.api.atlas.mapper.DataSourceMapper;
import com.api.atlas.model.DataSource;
import com.api.atlas.model.DataSourceCreateDTO;
import com.api.atlas.model.DataSourceUpdateDTO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final SecretKey secretKey;

    public DataSourceService(DataSourceMapper dataSourceMapper, SecretKey secretKey) {
        this.dataSourceMapper = dataSourceMapper;
        this.secretKey = secretKey;
    }

    private String encrypt(String plaintext) {
        return EncryptionUtil.encrypt(plaintext, secretKey);
    }

    public DataSource create(DataSourceCreateDTO dto) {
        DataSource dataSource = new DataSource();
        dataSource.setName(dto.getName());
        dataSource.setType(dto.getType());
        dataSource.setHost(dto.getHost());
        dataSource.setPort(dto.getPort());
        dataSource.setDatabaseName(dto.getDatabaseName());
        dataSource.setUsername(dto.getUsername());
        dataSource.setPassword(encrypt(dto.getPassword()));
        dataSource.setApiKey(dto.getApiKey());
        dataSource.setStatus("ENABLED");
        dataSourceMapper.insert(dataSource);
        // Security (F3): never expose credentials to the client — DB keeps the ciphertext.
        dataSource.setPassword(null);
        dataSource.setApiKey(null);
        return dataSource;
    }

    @Transactional(readOnly = true)
    public DataSource getById(Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }
        // selectById includes password/api_key (BaseColumns) — decrypt only in DataSourceClientManager, never in responses.
        ds.setPassword(null);
        ds.setApiKey(null);
        return ds;
    }

    @Transactional(readOnly = true)
    public PageInfo<DataSource> list(String name, String type, String status, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<DataSource> list = dataSourceMapper.selectList(name, type, status);
        for (DataSource ds : list) {
            ds.setPassword(null);
            ds.setApiKey(null);
        }
        return new PageInfo<>(list);
    }

    public DataSource update(Long id, DataSourceUpdateDTO dto) {
        DataSource existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }

        if (dto.getName() != null) {
            existing.setName(dto.getName());
        }
        if (dto.getType() != null) {
            existing.setType(dto.getType());
        }
        if (dto.getHost() != null) {
            existing.setHost(dto.getHost());
        }
        if (dto.getPort() != null) {
            existing.setPort(dto.getPort());
        }
        if (dto.getDatabaseName() != null) {
            existing.setDatabaseName(dto.getDatabaseName());
        }
        if (dto.getUsername() != null) {
            existing.setUsername(dto.getUsername());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            existing.setPassword(encrypt(dto.getPassword()));
        }
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            existing.setApiKey(dto.getApiKey());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }

        dataSourceMapper.updateById(existing);
        DataSource updated = dataSourceMapper.selectById(id);
        updated.setPassword(null);
        updated.setApiKey(null);
        return updated;
    }

    public void delete(Long id) {
        DataSource existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }
        int count = dataSourceMapper.countInterfacesByDataSourceId(id);
        if (count > 0) {
            throw new IllegalStateException("Cannot delete: datasource has " + count + " interfaces");
        }
        dataSourceMapper.deleteById(id);
    }

    public void updateStatus(Long id, String status) {
        DataSource existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }
        existing.setStatus(status);
        dataSourceMapper.updateById(existing);
    }
}
