package com.app.communityhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CommunityHubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityHubBackendApplication.class, args);
    }

}
