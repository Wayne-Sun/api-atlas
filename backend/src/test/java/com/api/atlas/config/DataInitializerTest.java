package com.api.atlas.config;

import com.api.atlas.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DataInitializerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserMapper userMapper;

    @Test
    void run_UnderTestProfile_SeedsNoAdminUser() {
        assertThat(userMapper.selectByUsername("admin")).isNull();
    }

    @Test
    void dataInitializer_UnderTestProfile_IsNotInstantiated() {
        assertThat(applicationContext.getBeanNamesForType(DataInitializer.class)).isEmpty();
    }
}
