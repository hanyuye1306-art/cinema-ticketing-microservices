package com.gouyan.web.controller.bill;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.system.domin.SysBill;
import com.gouyan.system.domin.vo.SysBillVo;
import com.gouyan.system.service.impl.SysBillServiceImpl;
import com.gouyan.web.controller.bill.service.BillMovieQueryService;
import com.gouyan.web.controller.bill.service.BillPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author: xianxian
 * @Create: 2020-11-30 14:27
 */
@RestController
public class SysBillController extends BaseController {

    @Autowired
    private SysBillServiceImpl sysBillService;

    @Autowired
    private BillMovieQueryService billMovieQueryService;

    @Autowired
    private BillPaymentService billPaymentService;

    @GetMapping("/sysBill")
    public ResponseResult findAll(SysBill sysBill){
        startPage();
        List<SysBill> data = sysBillService.findAll(sysBill);
        return getResult(data);
    }

    @GetMapping("/sysBill/{id}")
    public ResponseResult findById(@PathVariable Long id){
        return getResult(sysBillService.findById(id));
    }

    /**
     * 通过 OpenFeign 调用电影服务查询订单关联的电影信息。
     */
    @GetMapping("/sysBill/movie/{movieId}")
    public ResponseResult findMovieById(@PathVariable Long movieId,
                                        @RequestHeader(value = "Token", required = false) String token){
        return billMovieQueryService.findMovieById(movieId, token);
    }

    @PostMapping("/sysBill")
    public ResponseResult add(@Validated @RequestBody SysBill sysBill){
        System.out.println(sysBill);
        Object obj = sysBillService.add(sysBill);
        if(obj instanceof Integer){
            return getResult((Integer) obj);
        }
        return getResult(obj);
    }

    @PutMapping("/sysBill")
    public ResponseResult update(@RequestBody SysBillVo sysBillVo){
        return getResult(billPaymentService.updateBill(sysBillVo));
    }

    @DeleteMapping("/sysBill/{ids}")
    public ResponseResult delete(@PathVariable Long[] ids){
        return getResult(sysBillService.delete(ids));
    }

    @GetMapping("todayBoxOffice")
    public ResponseResult todayBoxOffice(){
        return getResult(sysBillService.todayBoxOffice());
    }

}
