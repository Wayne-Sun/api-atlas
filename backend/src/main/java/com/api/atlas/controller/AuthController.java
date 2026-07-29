package com.api.atlas.controller;

import com.api.atlas.model.LoginRequest;
import com.api.atlas.model.LoginResponse;
import com.api.atlas.model.R;
import com.api.atlas.model.TokenSession;
import com.api.atlas.model.User;
import com.api.atlas.model.UserInfoDTO;
import com.api.atlas.service.JwtTokenService;
import com.api.atlas.service.RedisTokenService;
import com.api.atlas.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final RedisTokenService redisTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RSAPublicKey publicKey;
    private final long accessTokenExpiration;

    public AuthController(UserService userService,
                          JwtTokenService jwtTokenService,
                          RedisTokenService redisTokenService,
                          RSAPublicKey publicKey,
                          @Value("${atlas.jwt.access-token-expiration:1800}") long accessTokenExpiration) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.redisTokenService = redisTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.publicKey = publicKey;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @PostMapping("/login")
    public ResponseEntity<R<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        // Load user
        User user = userService.getUserByUsername(request.getUsername());

        // Check if user exists
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(R.error(401, "Invalid username or password"));
        }

        // Check if account is disabled
        if ("DISABLED".equals(user.getStatus())) {
            return ResponseEntity.status(401)
                    .body(R.error(401, "Account disabled"));
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(R.error(401, "Invalid username or password"));
        }

        // Generate tokens
        String accessToken = jwtTokenService.generateAccessToken(user.getUsername(), user.getRole());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getUsername());

        // Extract jti from the generated access token for Redis session storage
        Jwt jwt = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(accessToken);
        String jti = jwt.getId();

        // Create and save token session to Redis
        TokenSession session = new TokenSession();
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setRole(user.getRole());
        session.setCreatedAt(LocalDateTime.now());
        redisTokenService.saveToken(jti, session, accessTokenExpiration);

        // Build user info DTO (never expose password hash)
        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setRole(user.getRole());

        // Build response
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(accessTokenExpiration);
        response.setTokenType("Bearer");
        response.setUser(userInfo);

        return ResponseEntity.ok(R.ok(response));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String jti = jwt.getId();
            if (jti != null) {
                redisTokenService.removeToken(jti);
            }
        }
        return R.ok(null);
    }

    @GetMapping("/me")
    public R<UserInfoDTO> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);

        if (user == null) {
            return R.error(404, "User not found");
        }

        // Build user info DTO (never expose password hash)
        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setRole(user.getRole());

        return R.ok(userInfo);
    }
}
