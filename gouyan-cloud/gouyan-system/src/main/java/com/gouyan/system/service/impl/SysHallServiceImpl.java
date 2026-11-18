package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysHall;
import com.gouyan.system.mapper.SysHallMapper;
import com.gouyan.system.service.SysHallService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;


@Service
public class SysHallServiceImpl implements SysHallService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    private SysHallMapper sysHallMapper;

    @Override
    public List<SysHall> findAll(SysHall sysHall) {
        return sysHallMapper.findAll(sysHall);
    }

    @Override
    public SysHall findByCinemaIdAndHallId(SysHall sysHall) {
        return sysHallMapper.findByCinemaIdAndHallId(sysHall);
    }

    @Override
    public int add(SysHall sysHall) {
        return sysHallMapper.add(sysHall);
    }

    @Override
    public int update(SysHall sysHall) {
        return sysHallMapper.update(sysHall);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(SysHall[] sysHalls) {
        adminDeleteGuard.checkHalls(sysHalls);
        int rows = 0;
        for (SysHall sysHall : sysHalls) {
            rows += sysHallMapper.delete(sysHall);
        }
        return rows;
    }
}
