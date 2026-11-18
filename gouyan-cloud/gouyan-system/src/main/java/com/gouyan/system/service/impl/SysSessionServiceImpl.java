package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysSession;
import com.gouyan.system.domin.vo.SysSessionVo;
import com.gouyan.system.mapper.SysSessionMapper;
import com.gouyan.system.service.SysSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import com.gouyan.common.exception.DataNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysSessionServiceImpl implements SysSessionService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    private SysSessionMapper sysSessionMapper;

    @Override
    public List<SysSession> findByVo(SysSessionVo sysSessionVo) {
        return sysSessionMapper.findByVo(sysSessionVo);
    }

    @Override
    public SysSession findById(Long id) {
        return sysSessionMapper.findById(id);
    }

    @Override
    public SysSession findOne(Long id){
        return sysSessionMapper.findOne(id);
    }

    @Override
    public int add(SysSession sysSession) {
        return sysSessionMapper.add(sysSession);
    }

    @Override
    public int update(SysSession sysSession) {
        SysSession original = sysSessionMapper.findOne(sysSession.getSessionId());
        if (original == null) throw new DataNotFoundException("场次不存在");
        if (adminDeleteGuard.hasSessionOrders(sysSession.getSessionId())) {
            if (!Objects.equals(original.getCinemaId(), sysSession.getCinemaId())
                    || !Objects.equals(original.getHallId(), sysSession.getHallId())
                    || !Objects.equals(original.getMovieId(), sysSession.getMovieId())) {
                throw new DataNotFoundException("场次已有订单，不能更换影院、影厅或电影");
            }
            sysSession.setSessionSeats(original.getSessionSeats());
            sysSession.setSeatNums(original.getSeatNums());
        }
        return sysSessionMapper.update(sysSession);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long[] ids) {
        adminDeleteGuard.check("sysSession", ids);
        int rows = 0;
        for(Long id : ids){
            rows += sysSessionMapper.delete(id);
        }
        return rows;
    }

    @Override
    public List<SysSession> findByCinemaAndMovie(Long cinemaId, Long movieId) {
        return sysSessionMapper.findByCinemaAndMovie(cinemaId, movieId);
    }
}
