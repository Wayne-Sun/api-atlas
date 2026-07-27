package com.api.atlas;

import com.api.atlas.service.ApiInterfaceService;
import com.api.atlas.service.DataSourceClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContextStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads_WithoutCircularDependency() {
        assertThat(context.getBean(DataSourceClientManager.class)).isNotNull();
        assertThat(context.getBean(ApiInterfaceService.class)).isNotNull();
    }
}
