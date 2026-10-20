package com.gouyan.system.service;

import com.gouyan.system.domin.SysCinemaArea;

import java.util.List;

public interface SysCinemaAreaService {

    List<SysCinemaArea> findAll();

    SysCinemaArea findById(Long id);

    int add(SysCinemaArea sysCinemaArea);

    int update(SysCinemaArea sysCinemaArea);

    int delete(Long[] ids);

}
