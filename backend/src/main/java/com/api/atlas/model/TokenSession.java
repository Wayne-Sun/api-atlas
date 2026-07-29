package com.api.atlas.model;

import java.time.LocalDateTime;

public class TokenSession {

    private Long userId;
    private String username;
    private String role;
    private LocalDateTime createdAt;

    public TokenSession() {
    }

    public TokenSession(Long userId, String username, String role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
