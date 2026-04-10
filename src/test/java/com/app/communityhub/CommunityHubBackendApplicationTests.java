package com.app.communityhub;

import com.app.communityhub.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommunityHubBackendApplicationTests extends IntegrationTestSupport {

    @Test
    void contextLoads() {
    }

}
