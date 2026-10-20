package com.gouyan.system.service;

import com.gouyan.system.domin.SysUserHobby;

import java.util.List;

public interface SysUserHobbyService {

    List<SysUserHobby> findAll();

    SysUserHobby findById(Long id);

    int add(SysUserHobby sysUserHobby);

    int update(SysUserHobby sysUserHobby);

    int delete(Long[] ids);

}
