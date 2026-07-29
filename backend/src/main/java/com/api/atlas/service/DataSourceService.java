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

    private String decrypt(String ciphertext) {
        return EncryptionUtil.decrypt(ciphertext, secretKey);
    }

    private String getDecryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return encryptedPassword;
        }
        return decrypt(encryptedPassword);
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
        return dataSource;
    }

    @Transactional(readOnly = true)
    public DataSource getById(Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }
        ds.setPassword(getDecryptPassword(ds.getPassword()));
        return ds;
    }

    @Transactional(readOnly = true)
    public PageInfo<DataSource> list(String name, String type, String status, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<DataSource> list = dataSourceMapper.selectList(name, type, status);
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
        if (dto.getPassword() != null) {
            existing.setPassword(encrypt(dto.getPassword()));
        }
        if (dto.getApiKey() != null) {
            existing.setApiKey(dto.getApiKey());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }

        dataSourceMapper.updateById(existing);
        return dataSourceMapper.selectById(id);
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
