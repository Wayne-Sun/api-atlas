package com.api.atlas.service;

import com.api.atlas.config.SecurityUtil;
import com.api.atlas.mapper.UserMapper;
import com.api.atlas.model.User;
import com.api.atlas.model.UserCreateDTO;
import com.api.atlas.model.UserUpdateDTO;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUsername).thenReturn("admin");
    }

    @AfterEach
    void tearDown() {
        if (securityUtil != null) {
            securityUtil.close();
        }
    }

    @Test
    @DisplayName("创建用户 - 有效DTO返回用户")
    void create_ValidDTO_ReturnsUser() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");
        dto.setDisplayName("Test User");
        dto.setRole("USER");

        when(userMapper.selectByUsername("testuser")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        User result = userService.create(dto);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).startsWith("$2a$");
        assertThat(result.getDisplayName()).isEqualTo("Test User");
        assertThat(result.getRole()).isEqualTo("USER");
        assertThat(result.getStatus()).isEqualTo("ENABLED");

        verify(userMapper).selectByUsername("testuser");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("创建用户 - 重复用户名抛出异常")
    void create_DuplicateUsername_ThrowsException() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("existing");
        dto.setPassword("password123");
        dto.setDisplayName("Existing User");

        User existing = new User();
        existing.setUsername("existing");
        when(userMapper.selectByUsername("existing")).thenReturn(existing);

        assertThatThrownBy(() -> userService.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("创建用户 - 默认角色为USER")
    void create_NullRole_DefaultsToUser() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");
        dto.setDisplayName("Test User");
        // role is null

        when(userMapper.selectByUsername("testuser")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        User result = userService.create(dto);

        assertThat(result.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("按ID查询 - 存在返回用户")
    void getById_ExistingUser_ReturnsUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        when(userMapper.selectById(1L)).thenReturn(user);

        User result = userService.getById(1L);

        assertThat(result).isSameAs(user);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("按ID查询 - 不存在抛出异常")
    void getById_NonExisting_ThrowsException() {
        when(userMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getById(9999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("按用户名查询 - 存在返回用户")
    void getUserByUsername_ExistingUser_ReturnsUser() {
        User user = new User();
        user.setUsername("testuser");
        when(userMapper.selectByUsername("testuser")).thenReturn(user);

        User result = userService.getUserByUsername("testuser");

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("按用户名查询 - 不存在返回null")
    void getUserByUsername_NonExisting_ReturnsNull() {
        when(userMapper.selectByUsername("nonexistent")).thenReturn(null);

        User result = userService.getUserByUsername("nonexistent");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("查询列表 - 返回分页结果")
    void list_WithFilters_ReturnsPage() {
        User u1 = new User();
        u1.setId(1L);
        u1.setUsername("user1");
        User u2 = new User();
        u2.setId(2L);
        u2.setUsername("user2");
        List<User> mockList = List.of(u1, u2);

        when(userMapper.selectList(any(), any(), any())).thenReturn(mockList);

        PageInfo<User> result = userService.list(null, null, null, 1, 10);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("更新用户 - 有效DTO更新成功")
    void update_ValidData_UpdatesUser() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("olduser");
        existing.setDisplayName("Old Name");
        existing.setRole("USER");

        User updated = new User();
        updated.setId(1L);
        updated.setUsername("newuser");
        updated.setDisplayName("New Name");
        updated.setRole("ADMIN");

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setUsername("newuser");
        dto.setDisplayName("New Name");
        dto.setRole("ADMIN");

        when(userMapper.selectById(1L)).thenReturn(existing, updated);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        User result = userService.update(1L, dto);

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getDisplayName()).isEqualTo("New Name");
        assertThat(result.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("更新用户 - 不能修改自己的角色")
    void update_OwnRole_ThrowsIllegalStateException() {
        securityUtil.when(SecurityUtil::getCurrentUsername).thenReturn("testuser");

        User existing = new User();
        existing.setId(1L);
        existing.setUsername("testuser");
        existing.setRole("ADMIN");

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setRole("USER");

        when(userMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> userService.update(1L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot change your own role");
    }

    @Test
    @DisplayName("更新用户 - 不同用户可改角色")
    void update_OtherUserRole_UpdatesSuccessfully() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("otheruser");
        existing.setRole("USER");

        User updated = new User();
        updated.setId(1L);
        updated.setRole("ADMIN");

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setRole("ADMIN");

        when(userMapper.selectById(1L)).thenReturn(existing, updated);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        User result = userService.update(1L, dto);

        assertThat(result.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("删除用户 - 成功删除")
    void delete_ExistingUser_DeletesUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("otheruser");

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.deleteById(1L)).thenReturn(1);

        userService.delete(1L);

        verify(userMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除用户 - 不能删除自己")
    void delete_SelfUser_ThrowsException() {
        securityUtil.when(SecurityUtil::getCurrentUsername).thenReturn("selfuser");

        User user = new User();
        user.setId(1L);
        user.setUsername("selfuser");

        when(userMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete your own account");
    }

    @Test
    @DisplayName("更新状态 - 成功变更")
    void updateStatus_ValidTransition_Updates() {
        User user = new User();
        user.setId(1L);
        user.setUsername("otheruser");

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateStatus(1L, "DISABLED");

        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("更新状态 - 不能禁用自己")
    void updateStatus_SelfUser_ThrowsException() {
        securityUtil.when(SecurityUtil::getCurrentUsername).thenReturn("selfuser");

        User user = new User();
        user.setId(1L);
        user.setUsername("selfuser");

        when(userMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> userService.updateStatus(1L, "DISABLED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot disable your own account");
    }
}
