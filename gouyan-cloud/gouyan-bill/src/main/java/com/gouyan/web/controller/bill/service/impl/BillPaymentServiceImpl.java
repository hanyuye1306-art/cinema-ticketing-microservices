package com.gouyan.web.controller.bill.service.impl;

import com.gouyan.common.exception.DataNotFoundException;
import com.gouyan.system.domin.SysBill;
import com.gouyan.system.domin.SysMovie;
import com.gouyan.system.domin.SysSession;
import com.gouyan.system.domin.vo.SysBillVo;
import com.gouyan.system.mapper.SysBillMapper;
import com.gouyan.system.mapper.SysMovieMapper;
import com.gouyan.system.mapper.SysSessionMapper;
import com.gouyan.web.controller.bill.service.BillPaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentServiceImpl implements BillPaymentService {

    private final SysBillMapper sysBillMapper;
    private final SysSessionMapper sysSessionMapper;
    private final SysMovieMapper sysMovieMapper;

    public BillPaymentServiceImpl(SysBillMapper sysBillMapper,
                                  SysSessionMapper sysSessionMapper,
                                  SysMovieMapper sysMovieMapper) {
        this.sysBillMapper = sysBillMapper;
        this.sysSessionMapper = sysSessionMapper;
        this.sysMovieMapper = sysMovieMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBill(SysBillVo sysBillVo) {
        if (sysBillVo == null || sysBillVo.getSysBill() == null
                || sysBillVo.getSysBill().getBillId() == null) {
            throw new DataNotFoundException("订单信息不能为空");
        }

        SysBill requestedBill = sysBillVo.getSysBill();
        SysBill persistedBill = sysBillMapper.findByIdForUpdate(requestedBill.getBillId());
        if (persistedBill == null) {
            throw new DataNotFoundException("订单不存在");
        }

        if (!Boolean.TRUE.equals(requestedBill.getBillState())) {
            return sysBillMapper.update(requestedBill);
        }

        // 已支付订单重复提交时直接返回成功，避免重复占座和重复累计票房。
        if (Boolean.TRUE.equals(persistedBill.getBillState())) {
            return 1;
        }

        SysSession session = sysSessionMapper.findOne(persistedBill.getSessionId());
        if (session == null) {
            throw new DataNotFoundException("订单关联的场次不存在，支付已取消");
        }
        SysMovie movie = sysMovieMapper.findOne(session.getMovieId());
        if (movie == null) {
            throw new DataNotFoundException("订单关联的电影不存在，支付已取消");
        }
        if (sysBillVo.getSessionSeats() == null || sysBillVo.getSessionSeats().trim().isEmpty()) {
            throw new DataNotFoundException("支付后的场次座位信息不能为空");
        }

        requestedBill.setSessionId(persistedBill.getSessionId());
        if (sysBillMapper.update(requestedBill) != 1) {
            throw new IllegalStateException("订单支付状态更新失败");
        }

        session.setSessionSeats(sysBillVo.getSessionSeats());
        if (sysSessionMapper.update(session) != 1) {
            throw new IllegalStateException("场次座位更新失败");
        }

        int seatCount = persistedBill.getSeats().split(",").length;
        movie.setMovieBoxOffice(movie.getMovieBoxOffice() + seatCount * session.getSessionPrice());
        if (sysMovieMapper.update(movie) != 1) {
            throw new IllegalStateException("电影票房更新失败");
        }
        return 1;
    }
}
