package com.api.atlas.service;

import com.api.atlas.config.SecurityUtil;
import com.api.atlas.mapper.UserMapper;
import com.api.atlas.model.User;
import com.api.atlas.model.UserCreateDTO;
import com.api.atlas.model.UserUpdateDTO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional
    public User create(UserCreateDTO dto) {
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(dto.getPassword()));
        user.setDisplayName(dto.getDisplayName());
        user.setStatus("ENABLED");
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        // Audit fields handled by AuditInterceptor

        userMapper.insert(user);
        return user;
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new NoSuchElementException("User not found: " + id);
        }
        return user;
    }

    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public PageInfo<User> list(String username, String role, String status, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = userMapper.selectList(username, role, status);
        return new PageInfo<>(list);
    }

    @Transactional
    public User update(Long id, UserUpdateDTO dto) {
        User existing = getById(id);

        // Cannot change own role
        String currentUsername = SecurityUtil.getCurrentUsername();
        if (existing.getUsername().equals(currentUsername)
                && dto.getRole() != null
                && !dto.getRole().equals(existing.getRole())) {
            throw new IllegalStateException("Cannot change your own role");
        }

        if (dto.getUsername() != null) {
            existing.setUsername(dto.getUsername());
        }
        if (dto.getPassword() != null) {
            existing.setPassword(new BCryptPasswordEncoder().encode(dto.getPassword()));
        }
        if (dto.getDisplayName() != null) {
            existing.setDisplayName(dto.getDisplayName());
        }
        if (dto.getRole() != null) {
            existing.setRole(dto.getRole());
        }

        userMapper.updateById(existing);
        return userMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        User user = getById(id);
        String currentUsername = SecurityUtil.getCurrentUsername();
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("Cannot delete your own account");
        }
        userMapper.deleteById(id);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        User user = getById(id);
        String currentUsername = SecurityUtil.getCurrentUsername();
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("Cannot disable your own account");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }
}
