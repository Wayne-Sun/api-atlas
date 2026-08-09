package com.api.atlas.config;

import com.api.atlas.mapper.UserMapper;
import com.api.atlas.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserMapper userMapper;

    @Value("${atlas.admin.default-password:#{null}}")
    private String defaultPassword;

    public DataInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectList(null, null, null).isEmpty()) {
            String password;
            if (defaultPassword != null && !defaultPassword.isBlank()) {
                if ("CHANGE_ME".equals(defaultPassword)) {
                    throw new IllegalStateException("atlas.admin.default-password must be changed from default");
                }
                password = defaultPassword;
            } else {
                password = java.util.UUID.randomUUID().toString();
            }

            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(new BCryptPasswordEncoder().encode(password));
            admin.setDisplayName("Administrator");
            admin.setRole("ADMIN");
            admin.setStatus("ENABLED");
            // Audit fields handled by AuditInterceptor

            userMapper.insert(admin);

            log.warn("Default admin user created — configure admin credentials via atlas.admin settings before first login");
        }
    }
}
