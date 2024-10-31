package com.douwen.my_gateway;

import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.web.server.ServerWebExchange;

public class CustomRequest extends DefaultRequest<ServerWebExchange> {

    public CustomRequest(ServerWebExchange context) {
        super(context);
    }
}