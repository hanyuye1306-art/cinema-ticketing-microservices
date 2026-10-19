package com.gouyan.web.controller.bill.service.impl;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.web.controller.bill.client.MovieFeignClient;
import com.gouyan.web.controller.bill.service.BillMovieQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BillMovieQueryServiceImpl implements BillMovieQueryService {

    private final MovieFeignClient movieFeignClient;

    public BillMovieQueryServiceImpl(MovieFeignClient movieFeignClient) {
        this.movieFeignClient = movieFeignClient;
    }

    @Override
    public ResponseResult findMovieById(Long movieId) {
        log.info("Bill service is querying movie {} through OpenFeign client gouyan-movie", movieId);
        ResponseResult result = movieFeignClient.findById(movieId);
        log.info("Bill service received movie {} response through OpenFeign, code={}",
                movieId, result.get("code"));
        return result;
    }
}
