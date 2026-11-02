package com.gouyan.web.controller.bill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;

@Service
public class BillPaymentServiceImpl implements BillPaymentService {

    private static final Pattern SEAT_PATTERN = Pattern.compile("(\\d+)排(\\d+)[号座]");

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
        if (persistedBill.getBillDate() != null
                && new Date().getTime() - persistedBill.getBillDate().getTime() > 15 * 60 * 1000L) {
            throw new IllegalStateException("订单已超过15分钟支付时限，请重新下单");
        }

        SysSession session = sysSessionMapper.findOneForUpdate(persistedBill.getSessionId());
        if (session == null) {
            throw new DataNotFoundException("订单关联的场次不存在，支付已取消");
        }
        SysMovie movie = sysMovieMapper.findOne(session.getMovieId());
        if (movie == null) {
            throw new DataNotFoundException("订单关联的电影不存在，支付已取消");
        }
        requestedBill.setSessionId(persistedBill.getSessionId());
        if (sysBillMapper.update(requestedBill) != 1) {
            throw new IllegalStateException("订单支付状态更新失败");
        }

        int seatCount = markPurchasedSeats(session, persistedBill.getSeats());
        session.setSeatNums((session.getSeatNums() == null ? 0 : session.getSeatNums()) + seatCount);
        if (sysSessionMapper.update(session) != 1) {
            throw new IllegalStateException("场次座位更新失败");
        }

        movie.setMovieBoxOffice(movie.getMovieBoxOffice() + seatCount * session.getSessionPrice());
        if (sysMovieMapper.update(movie) != 1) {
            throw new IllegalStateException("电影票房更新失败");
        }
        return 1;
    }

    /**
     * 以数据库中的场次座位为准完成占座，拒绝前端伪造的整份座位图和重复售卖。
     */
    private int markPurchasedSeats(SysSession session, String purchasedSeats) {
        JSONObject seatMap = JSON.parseObject(session.getSessionSeats());
        JSONArray seats = JSON.parseArray(purchasedSeats);
        if (seats == null || seats.isEmpty()) {
            throw new DataNotFoundException("订单座位不能为空");
        }
        for (Object value : seats) {
            Matcher matcher = SEAT_PATTERN.matcher(String.valueOf(value));
            if (!matcher.matches()) {
                throw new DataNotFoundException("订单座位格式错误");
            }
            JSONArray row = seatMap.getJSONArray(matcher.group(1));
            int columnIndex = Integer.parseInt(matcher.group(2)) - 1;
            if (row == null || columnIndex < 0 || columnIndex >= row.size()) {
                throw new DataNotFoundException("订单座位不存在");
            }
            int state = row.getIntValue(columnIndex);
            if (state == 1 || state == 3) {
                throw new IllegalStateException("座位已不可购买，请重新选座");
            }
            row.set(columnIndex, 3);
        }
        session.setSessionSeats(seatMap.toJSONString());
        return seats.size();
    }
}
