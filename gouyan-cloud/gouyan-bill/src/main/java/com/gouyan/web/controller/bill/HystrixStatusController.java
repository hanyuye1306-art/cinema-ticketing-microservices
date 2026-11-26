package com.gouyan.web.controller.bill;

import com.gouyan.common.response.ResponseResult;
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 提供当前 Bill 实例的电影查询断路器状态与事件计数。
 */
@Profile("hystrix-demo")
@RestController
public class HystrixStatusController {

    // 该接口没有业务参数，返回当前 Bill 实例内的命令指标。
    @GetMapping("/sysBill/hystrix/movie")
    public ResponseResult movieCircuitStatus() {
        ResponseResult result = ResponseResult.success();
        List<String> availableCommandKeys = new ArrayList<>();
        HystrixCommandMetrics metrics = null;
        long highestEventCount = -1;
        int candidateCount = 0;
        for (HystrixCommandMetrics candidate : HystrixCommandMetrics.getInstances()) {
            availableCommandKeys.add(candidate.getCommandKey().name());
            if (candidate.getCommandKey().name().equals("billMovieQuery")) {
                candidateCount++;
                long eventCount = candidate.getCumulativeCount(HystrixRollingNumberEvent.FAILURE)
                        + candidate.getCumulativeCount(HystrixRollingNumberEvent.SUCCESS)
                        + candidate.getCumulativeCount(HystrixRollingNumberEvent.TIMEOUT)
                        + candidate.getCumulativeCount(HystrixRollingNumberEvent.SHORT_CIRCUITED);
                if (eventCount > highestEventCount) {
                    highestEventCount = eventCount;
                    metrics = candidate;
                }
            }
        }
        if (metrics == null) {
            result.put("state", "NOT_INITIALIZED");
            result.put("availableCommandKeys", availableCommandKeys);
            return result;
        }

        HystrixCommandKey commandKey = metrics.getCommandKey();
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(commandKey);
        HystrixCommandMetrics.HealthCounts healthCounts = metrics.getHealthCounts();
        result.put("commandKey", commandKey.name());
        result.put("availableCommandKeys", availableCommandKeys);
        result.put("metricsCandidates", candidateCount);
        result.put("state", circuitBreaker.isOpen() ? "OPEN" : "CLOSED");
        result.put("totalRequests", healthCounts.getTotalRequests());
        result.put("errorCount", healthCounts.getErrorCount());
        result.put("errorPercentage", healthCounts.getErrorPercentage());
        result.put("rollingFailure", metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        result.put("rollingTimeout", metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        result.put("rollingShortCircuited", metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        result.put("rollingSuccess", metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        result.put("cumulativeFailure", metrics.getCumulativeCount(HystrixRollingNumberEvent.FAILURE));
        result.put("cumulativeTimeout", metrics.getCumulativeCount(HystrixRollingNumberEvent.TIMEOUT));
        result.put("cumulativeShortCircuited", metrics.getCumulativeCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        result.put("cumulativeSuccess", metrics.getCumulativeCount(HystrixRollingNumberEvent.SUCCESS));
        return result;
    }
}
