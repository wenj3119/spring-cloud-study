package com.douwen.my_gateway.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GatewayConfig {


    @Bean
    RestTemplate restTemplate(){
        return new RestTemplate();
    }

}
