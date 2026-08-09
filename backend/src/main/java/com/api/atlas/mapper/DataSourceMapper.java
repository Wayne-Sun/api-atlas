package com.api.atlas.mapper;

import com.api.atlas.model.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataSourceMapper {

    int insert(DataSource dataSource);

    DataSource selectById(Long id);

    List<DataSource> selectList(@Param("name") String name, @Param("type") String type, @Param("status") String status);

    List<DataSource> selectAll();

    int updateById(DataSource dataSource);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteById(Long id);

    int countInterfacesByDataSourceId(Long dataSourceId);
}
