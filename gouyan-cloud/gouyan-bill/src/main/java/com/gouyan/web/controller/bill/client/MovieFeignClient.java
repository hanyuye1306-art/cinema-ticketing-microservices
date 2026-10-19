package com.gouyan.web.controller.bill.client;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.web.controller.bill.config.FeignTokenRelayConfiguration;
import com.gouyan.web.controller.bill.fallback.MovieFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通过 Eureka 服务名调用电影服务。
 */
@FeignClient(name = "gouyan-movie", configuration = FeignTokenRelayConfiguration.class,
        fallbackFactory = MovieFeignFallbackFactory.class)
public interface MovieFeignClient {

    @GetMapping("/sysMovie/{id}")
    ResponseResult findById(@PathVariable("id") Long id, @RequestHeader("Token") String token);
}
