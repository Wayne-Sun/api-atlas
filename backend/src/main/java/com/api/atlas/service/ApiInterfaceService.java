package com.api.atlas.service;

import com.api.atlas.mapper.ApiInterfaceMapper;
import com.api.atlas.mapper.InterfaceParamMapper;
import com.api.atlas.model.ApiInterface;
import com.api.atlas.model.ApiInterfaceCreateDTO;
import com.api.atlas.model.ApiInterfaceUpdateDTO;
import com.api.atlas.model.InterfaceParam;
import com.api.atlas.model.ParamDef;
import com.api.atlas.service.executor.DatabaseQueryExecutor;
import com.api.atlas.service.executor.ElasticsearchQueryExecutor;
import com.api.atlas.service.executor.QueryResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Transactional
public class ApiInterfaceService implements DataSourceEventPublisher {

    private final ApiInterfaceMapper mapper;
    private final InterfaceParamMapper paramMapper;
    private final ParamExtractor paramExtractor;
    private final DatabaseQueryExecutor databaseQueryExecutor;
    private final ElasticsearchQueryExecutor esQueryExecutor;

    public ApiInterfaceService(ApiInterfaceMapper mapper, InterfaceParamMapper paramMapper,
                               ParamExtractor paramExtractor,
                               DatabaseQueryExecutor databaseQueryExecutor,
                               ElasticsearchQueryExecutor esQueryExecutor) {
        this.mapper = mapper;
        this.paramMapper = paramMapper;
        this.paramExtractor = paramExtractor;
        this.databaseQueryExecutor = databaseQueryExecutor;
        this.esQueryExecutor = esQueryExecutor;
    }

    @Transactional
    public ApiInterface create(ApiInterfaceCreateDTO dto) {
        String slug = dto.getEnglishName().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        ApiInterface entity = new ApiInterface();
        entity.setEnglishName(dto.getEnglishName());
        entity.setChineseName(dto.getChineseName());
        entity.setUrlSlug(slug);
        entity.setMethod(dto.getMethod());
        entity.setDataSourceId(dto.getDataSourceId());
        entity.setQueryType(dto.getQueryType());
        entity.setQueryContent(dto.getQueryContent());
        entity.setIsPaginated(dto.getIsPaginated());
        entity.setPageSize(dto.getPageSize());
        entity.setStatus("PENDING_TEST");

        mapper.insert(entity);

        List<ParamDef> paramDefs = paramExtractor.extract(dto.getQueryContent());
        if (!paramDefs.isEmpty()) {
            List<InterfaceParam> params = new ArrayList<>();
            for (ParamDef def : paramDefs) {
                InterfaceParam param = new InterfaceParam();
                param.setInterfaceId(entity.getId());
                param.setParamName(def.getName());
                param.setJavaType(def.getJavaType());
                param.setRemark(def.getRemark());
                param.setSortOrder(def.getSortOrder());
                params.add(param);
            }
            paramMapper.insertBatch(params);
        }

        return entity;
    }

    @Transactional(readOnly = true)
    public PageInfo<ApiInterface> list(Long dataSourceId, String name, String status,
                                       int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<ApiInterface> list = mapper.selectList(dataSourceId, name, status);
        return new PageInfo<>(list);
    }

    @Transactional(readOnly = true)
    public ApiInterface getById(Long id) {
        ApiInterface iface = mapper.selectById(id);
        if (iface == null) {
            throw new NoSuchElementException("Interface not found: " + id);
        }
        iface.setParams(paramMapper.selectByInterfaceId(id));
        return iface;
    }

    @Transactional
    public ApiInterface update(Long id, ApiInterfaceUpdateDTO dto) {
        ApiInterface existing = mapper.selectById(id);
        if (existing == null) {
            throw new NoSuchElementException("Interface not found: " + id);
        }

        if (dto.getEnglishName() != null) {
            existing.setEnglishName(dto.getEnglishName());
            existing.setUrlSlug(dto.getEnglishName().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", ""));
        }
        if (dto.getChineseName() != null) {
            existing.setChineseName(dto.getChineseName());
        }
        if (dto.getMethod() != null) {
            existing.setMethod(dto.getMethod());
        }
        if (dto.getDataSourceId() != null) {
            existing.setDataSourceId(dto.getDataSourceId());
        }
        if (dto.getQueryType() != null) {
            existing.setQueryType(dto.getQueryType());
        }
        String originalQueryContent = existing.getQueryContent();
        if (dto.getQueryContent() != null) {
            existing.setQueryContent(dto.getQueryContent());
        }
        if (dto.getIsPaginated() != null) {
            existing.setIsPaginated(dto.getIsPaginated());
        }
        if (dto.getPageSize() != null) {
            existing.setPageSize(dto.getPageSize());
        }

        mapper.updateById(existing);

        if (dto.getQueryContent() != null && !dto.getQueryContent().equals(originalQueryContent)) {
            paramMapper.deleteByInterfaceId(id);
            List<ParamDef> paramDefs = paramExtractor.extract(dto.getQueryContent());
            if (!paramDefs.isEmpty()) {
                List<InterfaceParam> params = new ArrayList<>();
                for (ParamDef def : paramDefs) {
                    InterfaceParam param = new InterfaceParam();
                    param.setInterfaceId(id);
                    param.setParamName(def.getName());
                    param.setJavaType(def.getJavaType());
                    param.setRemark(def.getRemark());
                    param.setSortOrder(def.getSortOrder());
                    params.add(param);
                }
                paramMapper.insertBatch(params);
            }
        }

        return mapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        ApiInterface iface = mapper.selectById(id);
        if (iface == null) {
            throw new NoSuchElementException("Interface not found: " + id);
        }
        if ("ONLINE".equals(iface.getStatus())) {
            throw new IllegalStateException("Cannot delete online interface: " + id);
        }
        paramMapper.deleteByInterfaceId(id);
        mapper.deleteById(id);
    }

    /**
     * Execute an interface test: resolve executor by queryType, run query, return result.
     */
    @Transactional(readOnly = true)
    public QueryResult testInterface(Long id, Map<String, Object> params, int pageNum, int pageSize) {
        ApiInterface iface = mapper.selectById(id);
        if (iface == null) {
            throw new NoSuchElementException("Interface not found: " + id);
        }

        // Check interface status — only PENDING_TEST and ONLINE can be tested
        if ("OFFLINE".equals(iface.getStatus())) {
            throw new IllegalStateException("Interface is offline: " + id);
        }

        Long dsId = iface.getDataSourceId();
        String queryType = iface.getQueryType();
        String queryContent = iface.getQueryContent();

        switch (queryType) {
            case "SQL":
                return databaseQueryExecutor.executeSql(dsId, queryContent, params, pageNum, pageSize);
            case "IBATIS":
                return databaseQueryExecutor.executeIbatis(dsId, queryContent, params, pageNum, pageSize);
            case "ESQL":
                return esQueryExecutor.executeEsql(dsId, queryContent, params, pageNum, pageSize);
            case "QUERY_DSL":
                return esQueryExecutor.executeQueryDsl(dsId, queryContent, params, pageNum, pageSize);
            default:
                throw new IllegalArgumentException("Unsupported query type: " + queryType);
        }
    }

    /**
     * Update interface status with state machine enforcement.
     */
    public void updateStatus(Long id, String newStatus) {
        ApiInterface iface = mapper.selectById(id);
        if (iface == null) {
            throw new NoSuchElementException("Interface not found: " + id);
        }

        String currentStatus = iface.getStatus();

        // Validate transition via state machine:
        // PENDING_TEST ──→ ONLINE ──→ OFFLINE
        //                    ↑           │
        //                    └───────────┘
        boolean valid = switch (currentStatus) {
            case "PENDING_TEST" -> "ONLINE".equals(newStatus);
            case "ONLINE" -> "OFFLINE".equals(newStatus);
            case "OFFLINE" -> "ONLINE".equals(newStatus);
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        mapper.updateStatus(id, newStatus);
    }

    @Override
    public void onDataSourceDisabled(Long datasourceId, String datasourceName) {
        List<ApiInterface> interfaces = mapper.selectByDataSourceId(datasourceId);
        for (ApiInterface iface : interfaces) {
            mapper.updateStatus(iface.getId(), "OFFLINE");
        }
    }
}
