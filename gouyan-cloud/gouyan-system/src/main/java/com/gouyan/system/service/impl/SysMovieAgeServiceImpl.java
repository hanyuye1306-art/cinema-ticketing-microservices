package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysMovieAge;
import com.gouyan.system.mapper.SysMovieAgeMapper;
import com.gouyan.system.service.SysMovieAgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysMovieAgeServiceImpl implements SysMovieAgeService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    SysMovieAgeMapper sysMovieAgeMapper;

    @Override
    public List<SysMovieAge> findAll() {
        return sysMovieAgeMapper.findAll();
    }

    @Override
    public SysMovieAge findById(Long id) {
        return sysMovieAgeMapper.findById(id);
    }

    @Override
    public int add(SysMovieAge sysMovieAge) {
        return sysMovieAgeMapper.add(sysMovieAge);
    }

    @Override
    public int update(SysMovieAge sysMovieAge) {
        return sysMovieAgeMapper.update(sysMovieAge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long[] ids) {
        adminDeleteGuard.check("sysMovieAge", ids);
        int rows = 0;
        for(Long id : ids)
            rows += sysMovieAgeMapper.delete(id);
        return rows;
    }
}
