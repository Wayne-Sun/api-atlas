package com.api.atlas.mapper;

import com.api.atlas.model.ApiInterface;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApiInterfaceMapper {

    int insert(ApiInterface entity);

    ApiInterface selectById(Long id);

    List<ApiInterface> selectList(@Param("dataSourceId") Long dataSourceId,
                                  @Param("name") String name,
                                  @Param("status") String status);

    List<ApiInterface> selectByDataSourceId(Long dataSourceId);

    int updateById(ApiInterface entity);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteById(Long id);
}
