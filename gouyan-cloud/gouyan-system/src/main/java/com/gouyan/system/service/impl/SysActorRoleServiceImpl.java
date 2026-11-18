package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysActorRole;
import com.gouyan.system.mapper.SysActorRoleMapper;
import com.gouyan.system.service.SysActorRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysActorRoleServiceImpl implements SysActorRoleService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    SysActorRoleMapper sysActorRoleMapper;

    @Override
    public List<SysActorRole> findAll() {
        return sysActorRoleMapper.findAll();
    }

    @Override
    public SysActorRole findById(Long id) {
        return sysActorRoleMapper.findById(id);
    }

    @Override
    public int add(SysActorRole sysActorRole) {
        return sysActorRoleMapper.add(sysActorRole);
    }

    @Override
    public int update(SysActorRole sysActorRole) {
        return sysActorRoleMapper.update(sysActorRole);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long[] ids) {
        adminDeleteGuard.check("sysActorRole", ids);
        int rows = 0;
        for(Long id : ids){
            rows += sysActorRoleMapper.delete(id);
        }
        return rows;
    }
}
