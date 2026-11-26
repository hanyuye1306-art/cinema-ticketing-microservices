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

    // SysBillController → BillMovieQueryServiceImpl → 本接口的 Feign 代理。
    // id 替换远端路径变量，Token 放入 HTTP 请求头。Ribbon 根据 gouyan-movie 服务名选取实例。
    /**
     * 按电影服务名解析目标实例并请求详情。
     * @param id 绑定到远端路径的电影主键
     * @param token 传给电影服务的会话请求头
     * @return 电影详情响应或客户端降级响应
     */
    @GetMapping("/sysMovie/{id}")
    ResponseResult findById(@PathVariable("id") Long id, @RequestHeader("Token") String token);
}
