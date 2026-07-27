package com.api.atlas.mapper;

import com.api.atlas.model.InterfaceParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InterfaceParamMapper {

    int insertBatch(List<InterfaceParam> params);

    List<InterfaceParam> selectByInterfaceId(Long interfaceId);

    int deleteByInterfaceId(Long interfaceId);
}
