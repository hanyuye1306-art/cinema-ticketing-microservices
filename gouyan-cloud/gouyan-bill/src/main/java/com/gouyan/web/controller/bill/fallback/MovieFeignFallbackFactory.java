package com.gouyan.web.controller.bill.fallback;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.web.controller.bill.client.MovieFeignClient;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Movie Feign 调用失败、超时或被熔断后的结构化降级响应。
 */
@Slf4j
@Component
public class MovieFeignFallbackFactory implements FallbackFactory<MovieFeignClient> {

    @Override
    public MovieFeignClient create(Throwable cause) {
        String failureType = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        String failureMessage = cause == null ? "unknown" : cause.getMessage();
        log.warn("Movie Feign fallback triggered, type={}, message={}",
                failureType, failureMessage);

        return (movieId, token) -> {
            ResponseResult result = ResponseResult.error(503, "电影服务暂时不可用，已触发Hystrix降级");
            result.put("fallback", true);
            result.put("movieId", movieId);
            result.put("failureType", failureType);
            result.put("failureMessage", failureMessage);
            return result;
        };
    }
}
