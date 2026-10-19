package com.gouyan.web.controller.bill.service;

import com.gouyan.common.response.ResponseResult;

/**
 * 订单服务的只读电影信息查询业务。
 */
public interface BillMovieQueryService {

    ResponseResult findMovieById(Long movieId);
}
