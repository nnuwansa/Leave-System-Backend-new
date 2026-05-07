package com.LeaveDataManagementSystem.LeaveManagement.Config;



import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SmsConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}