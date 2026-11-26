package com.gouyan.web.controller.actor;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.system.domin.SysActorMovie;
import com.gouyan.system.service.impl.SysActorMovieServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
public class SysActorMovieController extends BaseController {

    @Autowired
    private SysActorMovieServiceImpl sysActorMovieService;

    // 读取电影、演员和角色之间的关联；查询条件由 URL 绑定到 SysActorMovie。
    @GetMapping("/sysActorMovie")
    public ResponseResult findAll(SysActorMovie sysActorMovie){
        startPage();
        return getResult(sysActorMovieService.findAll(sysActorMovie));
    }

    /** 新增关联，电影、演员和角色标识由请求体绑定。 */
    @PostMapping("/sysActorMovie")
    public ResponseResult add(@Validated @RequestBody SysActorMovie sysActorMovie){
        return getResult(sysActorMovieService.add(sysActorMovie));
    }

    @DeleteMapping("/sysActorMovie/{movieId}/{actorId}/{actorRoleId}")
    public ResponseResult delete(@PathVariable Long movieId, @PathVariable Long actorId, @PathVariable Long actorRoleId){
        return getResult(sysActorMovieService.delete(new SysActorMovie(movieId, actorId, actorRoleId)));
    }

}
