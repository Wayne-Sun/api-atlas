package com.api.atlas.mapper;

import com.api.atlas.config.AuditInterceptor;
import com.api.atlas.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditInterceptor.class)
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("插入有效用户 - 返回生成的主键 ID 且审计字段非空")
    void insert_ValidUser_ReturnsGeneratedId() {
        User user = createTestUser("admin");
        int rows = userMapper.insert(user);
        assertThat(rows).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        User found = userMapper.selectById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCreatedBy()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getLastModifiedBy()).isNotNull();
        assertThat(found.getLastModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("按 ID 查询 - 存在用户返回完整数据")
    void selectById_ExistingUser_ReturnsUser() {
        User user = createTestUser("selectuser");
        userMapper.insert(user);

        User found = userMapper.selectById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(user.getUsername());
        assertThat(found.getDisplayName()).isEqualTo(user.getDisplayName());
        assertThat(found.getRole()).isEqualTo("USER");
        assertThat(found.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("按用户名查询 - 存在用户返回匹配数据")
    void selectByUsername_ExistingUser_ReturnsUser() {
        User user = createTestUser("findme");
        userMapper.insert(user);

        User found = userMapper.selectByUsername(user.getUsername());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(user.getId());
        assertThat(found.getUsername()).isEqualTo(user.getUsername());
    }

    @Test
    @DisplayName("条件查询列表 - 按角色过滤返回正确结果")
    void selectList_WithFilters_ReturnsFilteredResults() {
        User admin = createTestUser("admin1");
        admin.setRole("ADMIN");
        userMapper.insert(admin);

        User normal = createTestUser("user1");
        normal.setRole("USER");
        userMapper.insert(normal);

        List<User> admins = userMapper.selectList(null, "ADMIN", null);
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getRole()).isEqualTo("ADMIN");

        List<User> users = userMapper.selectList(null, "USER", null);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("更新用户 - 字段正确更新")
    void updateById_ExistingUser_UpdatesFields() {
        User user = createTestUser("updateme");
        userMapper.insert(user);

        user.setDisplayName("Updated Name");
        int rows = userMapper.updateById(user);
        assertThat(rows).isEqualTo(1);

        User updated = userMapper.selectById(user.getId());
        assertThat(updated.getDisplayName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("更新状态 - 状态正确变更且审计字段自动填充")
    void updateStatus_ExistingUser_ChangesStatusAndPopulatesAuditFields() {
        User user = createTestUser("statustest");
        userMapper.insert(user);

        user.setStatus("DISABLED");
        int rows = userMapper.updateById(user);
        assertThat(rows).isEqualTo(1);

        User updated = userMapper.selectById(user.getId());
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
        assertThat(updated.getLastModifiedBy()).isNotNull();
        assertThat(updated.getLastModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("删除用户 - 物理删除成功")
    void deleteById_ExistingUser_RemovesUser() {
        User user = createTestUser("deleteme");
        userMapper.insert(user);

        int rows = userMapper.deleteById(user.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(userMapper.selectById(user.getId())).isNull();
    }

    @Test
    @DisplayName("重复用户名插入 - 抛出唯一键冲突异常")
    void insert_DuplicateUsername_ThrowsException() {
        String uniqueUsername = "dupuser-" + System.nanoTime();
        User user1 = new User();
        user1.setUsername(uniqueUsername);
        user1.setPassword("password123");
        user1.setDisplayName("Dup User 1");
        user1.setRole("USER");
        user1.setStatus("ENABLED");
        userMapper.insert(user1);

        User user2 = new User();
        user2.setUsername(uniqueUsername);
        user2.setPassword("password456");
        user2.setDisplayName("Dup User 2");
        user2.setRole("ADMIN");
        user2.setStatus("ENABLED");
        assertThatThrownBy(() -> userMapper.insert(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createTestUser(String username) {
        User user = new User();
        user.setUsername(username + "-" + System.nanoTime());
        user.setPassword("password123");
        user.setDisplayName("Test User");
        user.setRole("USER");
        user.setStatus("ENABLED");
        return user;
    }
}
