package com.gouyan.web.controller.bill.service.impl;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.web.controller.bill.client.MovieFeignClient;
import com.gouyan.web.controller.bill.service.BillMovieQueryService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class BillMovieQueryServiceImpl implements BillMovieQueryService {

    private final MovieFeignClient movieFeignClient;

    public BillMovieQueryServiceImpl(MovieFeignClient movieFeignClient) {
        this.movieFeignClient = movieFeignClient;
    }

    @Override
    @HystrixCommand(commandKey = "billMovieQuery", fallbackMethod = "findMovieByIdFallback",
            commandProperties = @HystrixProperty(
                    name = "execution.isolation.strategy", value = "SEMAPHORE"))
    public ResponseResult findMovieById(Long movieId) {
        log.info("Bill service is querying movie {} through OpenFeign client gouyan-movie", movieId);
        ResponseResult result = movieFeignClient.findById(movieId, currentToken());
        if (!Integer.valueOf(200).equals(result.get("code"))) {
            throw new IllegalStateException("Movie Feign degraded: failureType="
                    + result.get("failureType") + ", failureMessage=" + result.get("failureMessage"));
        }
        log.info("Bill service received movie {} response through OpenFeign, code={}", movieId, result.get("code"));
        return result;
    }

    public ResponseResult findMovieByIdFallback(Long movieId, Throwable cause) {
        log.warn("Bill movie query Hystrix fallback, movieId={}, type={}, message={}",
                movieId, cause.getClass().getSimpleName(), cause.getMessage());
        ResponseResult result = ResponseResult.error(503, "电影服务暂时不可用，已触发Hystrix降级");
        result.put("fallback", true);
        result.put("movieId", movieId);
        result.put("failureType", cause.getClass().getSimpleName());
        result.put("failureMessage", cause.getMessage());
        return result;
    }

    private String currentToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader("Token");
        return token == null ? "" : token;
    }
}
