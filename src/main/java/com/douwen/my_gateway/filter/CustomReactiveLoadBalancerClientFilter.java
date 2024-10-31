package com.douwen.my_gateway.filter;

import com.douwen.my_gateway.CustomRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class CustomReactiveLoadBalancerClientFilter extends ReactiveLoadBalancerClientFilter{
    public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150;
    private final LoadBalancerClientFactory clientFactory;
    private final GatewayLoadBalancerProperties properties;

    public CustomReactiveLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory, GatewayLoadBalancerProperties properties) {
        super(clientFactory, properties);
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    public int getOrder() {
        return LOAD_BALANCER_CLIENT_FILTER_ORDER;
    }

    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = (URI)exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        // 获取原始路由前缀 若包含 lb 则表示需要进行负载均衡
        String schemePrefix = (String)exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR);
        if (url != null && ("lb".equals(url.getScheme()) || "lb".equals(schemePrefix))) {
            ServerWebExchangeUtils.addOriginalRequestUrl(exchange, url);

            URI requestUri = (URI)exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            String serviceId = requestUri.getHost();
            Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator.getSupportedLifecycleProcessors(this.clientFactory.getInstances(serviceId, LoadBalancerLifecycle.class), RequestDataContext.class, ResponseData.class, ServiceInstance.class);
            // 自定义请求，将参数ServerWebExchange作为context传递，使得自定义负载均衡器中可以使用
            DefaultRequest<ServerWebExchange> lbRequest = new CustomRequest(exchange);
            return this.choose(lbRequest, serviceId, supportedLifecycleProcessors).flatMap(response -> {
                if (!response.hasServer()) {
                    supportedLifecycleProcessors.forEach((lifecycle) -> {
                        lifecycle.onComplete(new CompletionContext(CompletionContext.Status.DISCARD, lbRequest, response));
                    });
                    throw NotFoundException.create(this.properties.isUse404(), "Unable to find instance for " + url.getHost());
                } else {
                    ServiceInstance retrievedInstance = (ServiceInstance)response.getServer();
                    URI uri = exchange.getRequest().getURI();
                    String overrideScheme = retrievedInstance.isSecure() ? "https" : "http";
                    if (schemePrefix != null) {
                        overrideScheme = url.getScheme();
                    }

                    DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(retrievedInstance, overrideScheme);
                    URI requestUrl = this.reconstructURI(serviceInstance, uri);

                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, requestUrl);
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
                    supportedLifecycleProcessors.forEach((lifecycle) -> {
                        lifecycle.onStartRequest(lbRequest, response);
                    });
                }
                return chain.filter(getMutatedExchange(exchange));
            });
        } else {
            return chain.filter(exchange);
        }
    }

    private ResponseData buildResponseData(ServerWebExchange exchange, boolean useRawStatusCodes) {
        return useRawStatusCodes ? new ResponseData(exchange.getResponse(), new RequestData(exchange.getRequest())) : new ResponseData(exchange.getResponse(), new RequestData(exchange.getRequest()));
    }

    protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
        return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
    }

    /**
     * 获取对应负载均衡器并调用choose方法
     *
     * @param lbRequest
     * @param serviceId
     * @param supportedLifecycleProcessors
     * @return
     */
    private Mono<Response<ServiceInstance>> choose(Request<ServerWebExchange> lbRequest, String serviceId, Set<LoadBalancerLifecycle> supportedLifecycleProcessors) {
        ReactorLoadBalancer<ServiceInstance> loadBalancer = (ReactorLoadBalancer)this.clientFactory.getInstance(serviceId, ReactorServiceInstanceLoadBalancer.class);
        if (loadBalancer == null) {
            throw new NotFoundException("No loadbalancer available for " + serviceId);
        } else {
            supportedLifecycleProcessors.forEach((lifecycle) -> {
                lifecycle.onStart(lbRequest);
            });
            return loadBalancer.choose(lbRequest);
        }
    }

    /**
     * 若为指定接口，则方法body会被流式获取，由于该body只能获取一次，需要重新构造request
     *
     * @param exchange
     * @return
     */
    private ServerWebExchange getMutatedExchange(ServerWebExchange exchange) {
        // 获取请求路径和请求方法类型
        ServerHttpRequest context = exchange.getRequest();
        String path = context.getURI().getPath();
        HttpMethod method = context.getMethod();

        // 若不为指定接口则不需要重置请求
        if (false) {
            return exchange;
        }

        // 负载均衡器中若获取了body过后会将值设置到Attribute中
        String requestBody = exchange.getAttribute("REQUEST_BODY");
        if (Objects.isNull(requestBody)) {
            return exchange;
        }

        // 构造重置请求
        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return Flux.just(buffer);
            }
        };

        return exchange.mutate().request(mutatedRequest).build();
    }
}
