package com.gouyan.system.mapper;

import com.gouyan.system.domin.SysActorMovie;

import java.util.List;

public interface SysActorMovieMapper {
    List<SysActorMovie> findAll(SysActorMovie sysActorMovie);

    int add(SysActorMovie sysActorMovie);

    int delete(SysActorMovie sysActorMovie);
}
