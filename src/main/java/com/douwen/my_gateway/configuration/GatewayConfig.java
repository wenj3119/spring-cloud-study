package com.douwen.my_gateway.configuration;

import com.douwen.my_gateway.filter.CustomReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GatewayConfig {


    @Bean
    RestTemplate restTemplate(){
        return new RestTemplate();
    }

/*    @Bean
    public ReactorServiceInstanceLoadBalancer reactorServiceInstanceLoadBalancer(Environment environment, LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new CustomLoadBalancer(loadBalancerClientFactory.
                getLazyProvider(name, ServiceInstanceListSupplier.class),name);
    }*/

/*    @Bean
    public ReactiveLoadBalancerClientFilter reactiveLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory,
                                                                             GatewayLoadBalancerProperties properties) {
        return new CustomReactiveLoadBalancerClientFilter(clientFactory, properties);
    }*/

}
