package com.gouyan.system.service;

import com.gouyan.system.domin.SysMovie;
import com.gouyan.system.domin.SysMovieToCategory;

import java.util.List;

public interface SysMovieToCategoryService {

    List<SysMovieToCategory> findAll(SysMovieToCategory sysMovieToCategory);

    int add(SysMovieToCategory sysMovieToCategory);

    int delete(SysMovieToCategory sysMovieToCategory);

}
