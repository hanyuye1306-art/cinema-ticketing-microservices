package com.gouyan.web.controller.actor;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.system.domin.SysActor;
import com.gouyan.system.domin.SysMovie;
import com.gouyan.system.service.impl.SysActorServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 提供演员档案维护与包含作品信息的演员详情查询。 */
@RestController
public class SysActorController extends BaseController {
    @Autowired
    private SysActorServiceImpl sysActorService;

    /**
     * 将演员筛选条件交给服务层，并返回分页结果。
     * @param sysActor 从请求参数绑定的演员姓名、性别、学校等筛选条件
     * @return 演员列表及分页信息
     */
    @GetMapping("/sysActor")
    public ResponseResult findAll(SysActor sysActor){
        startPage();
        List<SysActor> data = sysActorService.findAll(sysActor);
        return getResult(data);
    }

    @GetMapping("/sysActor/{id}")
    public ResponseResult findById(@PathVariable Long id){
        return getResult(sysActorService.findById(id));
    }

    @PostMapping("/sysActor")
    public ResponseResult add(@Validated @RequestBody SysActor sysActor){
        return getResult(sysActorService.add(sysActor));
    }

    @PutMapping("/sysActor")
    public ResponseResult update(@Validated @RequestBody SysActor sysActor){
        return getResult(sysActorService.update(sysActor));
    }

    @DeleteMapping("/sysActor/{ids}")
    public ResponseResult delete(@PathVariable Long[] ids){
        return getResult(sysActorService.delete(ids));
    }

    /**
     * 查询演员详情，由服务层加载关联作品。
     * @param id 演员主键
     * @return 演员详情及关联电影信息
     */
    @GetMapping("/sysActor/find/{id}")
    public ResponseResult findActorById(@PathVariable Long id){
        return getResult(sysActorService.findActorById(id));
    }
}
