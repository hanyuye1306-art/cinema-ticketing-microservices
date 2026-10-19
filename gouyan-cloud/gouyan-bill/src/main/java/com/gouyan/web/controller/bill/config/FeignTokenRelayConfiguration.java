package com.gouyan.web.controller.bill.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 将入口请求的 JWT Token 传递给下游电影服务。
 */
@Configuration
public class FeignTokenRelayConfiguration {

    @Bean
    public RequestInterceptor tokenRelayRequestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("Token");
            if (token != null && !token.isEmpty()) {
                requestTemplate.header("Token", token);
            }
        };
    }
}
