package com.api.atlas.controller;

import com.api.atlas.model.TokenSession;
import com.api.atlas.model.User;
import com.api.atlas.service.JwtTokenService;
import com.api.atlas.service.RedisTokenService;
import com.api.atlas.service.UserService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class JwtClaimsValidationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RSAPublicKey rsaPublicKey;

    @Autowired
    private RSAPrivateKey rsaPrivateKey;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RedisTokenService redisTokenService;

    @MockitoBean(name = "redisTokenTemplate")
    private RedisTemplate<String, TokenSession> redisTokenTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void me_TokenWithoutIssuer_Returns401() throws Exception {
        // Signed with the SAME keypair as the decoder (test RSA keys from
        // application-test.yml) but the issuer claim is deliberately omitted.
        String token = buildTokenWithoutIssuer("testuser");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_TokenWithIssuer_Returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole("ADMIN");

        when(userService.getUserByUsername("testuser")).thenReturn(user);
        when(redisTokenService.exists(anyString())).thenReturn(true);

        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    private String buildTokenWithoutIssuer(String subject) {
        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .id(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
