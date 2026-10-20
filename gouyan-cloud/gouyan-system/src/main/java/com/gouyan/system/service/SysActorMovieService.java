package com.gouyan.system.service;

import com.gouyan.system.domin.SysActor;
import com.gouyan.system.domin.SysActorMovie;

import java.util.List;


public interface SysActorMovieService {
    List<SysActorMovie> findAll(SysActorMovie sysActorMovie);

    int add(SysActorMovie sysActorMovie);

    int delete(SysActorMovie sysActorMovie);
}
