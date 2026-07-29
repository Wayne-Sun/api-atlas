package com.api.atlas.controller;

import com.api.atlas.model.R;
import com.api.atlas.model.User;
import com.api.atlas.model.StatusUpdateDTO;
import com.api.atlas.model.UserCreateDTO;
import com.api.atlas.model.UserUpdateDTO;
import com.api.atlas.service.UserService;
import com.github.pagehelper.PageInfo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public R<List<User>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageInfo<User> pageInfo = userService.list(username, role, status, pageNum, pageSize);
        pageInfo.getList().forEach(u -> u.setPassword(null));
        return R.ok(pageInfo.getList(), pageInfo);
    }

    @GetMapping("/{id}")
    public R<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        user.setPassword(null);
        return R.ok(user);
    }

    @PostMapping
    public R<User> create(@Valid @RequestBody UserCreateDTO dto) {
        User created = userService.create(dto);
        created.setPassword(null);
        return R.ok(created);
    }

    @PutMapping("/{id}")
    public R<User> update(@PathVariable Long id, @RequestBody UserUpdateDTO dto) {
        User updated = userService.update(id, dto);
        updated.setPassword(null);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok(null);
    }

    @PatchMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        userService.updateStatus(id, dto.getStatus());
        return R.ok(null);
    }
}
