package com.api.atlas.mapper;

import com.api.atlas.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    int insert(User user);

    User selectById(Long id);

    User selectByUsername(@Param("username") String username);

    List<User> selectList(@Param("username") String username, @Param("role") String role, @Param("status") String status);

    int updateById(User user);

    int deleteById(Long id);
}
