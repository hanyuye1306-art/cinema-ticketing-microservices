package com.gouyan.web.controller.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gouyan.common.response.ResponseResult;
import com.gouyan.common.utils.StringUtil;
import com.gouyan.common.utils.JwtUtil;
import com.gouyan.system.domin.LoginUser;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.domin.vo.SysUserVo;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Collections;
import java.util.Objects;
import javax.validation.groups.Default;

@RestController
public class SysUserController extends BaseController {
    @Autowired
    private SysUserServiceImpl sysUserService;

    @GetMapping("/sysUser")
    public ResponseResult findAll(SysUser sysUser){
        requireAdministrator();
        startPage();
        List<SysUser> data = sysUserService.findAll(sysUser);
        return getResult(data);
    }

    @GetMapping("/sysUser/{id}")
    public ResponseResult findById(@PathVariable Long id){
        requireOwnerOrAdministrator(id);
        return getResult(sysUserService.findById(id));
    }

    /**
     * 添加用户请求，注册也在这里
     * @param sysUser
     * @return
     */
    @PostMapping("/sysUser")
    public ResponseResult add(@Validated({Default.class, SysUser.Create.class}) @RequestBody SysUser sysUser){
        requireAdministrator();
        return getResult(sysUserService.add(sysUser));
    }

    @PutMapping("/sysUser")
    public ResponseResult update(@Validated({Default.class, SysUser.Update.class}) @RequestBody SysUser sysUser){
        SysUser actor = requireOwnerOrAdministrator(sysUser.getUserId());
        if (!Long.valueOf(1L).equals(actor.getRoleId())) {
            sysUser.setRoleId(actor.getRoleId());
        }
        int rows = sysUserService.update(sysUser);
        ResponseResult result = getResult(rows);
        if (rows > 0 && actor.getUserId().equals(sysUser.getUserId())
                && !Objects.equals(actor.getUserName(), sysUser.getUserName())) {
            result.put("data", Collections.singletonMap("token", JwtUtil.sign(sysUser.getUserName())));
        }
        return result;
    }

    @DeleteMapping("/sysUser/{ids}")
    public ResponseResult delete(@PathVariable Long[] ids){
        SysUser actor = requireAdministrator();
        if (java.util.Arrays.asList(ids).contains(actor.getUserId())) {
            throw new AuthenticationException("不能删除当前登录账号");
        }
        return getResult(sysUserService.delete(ids));
    }

    //用户登录
    @RequestMapping("/sysUser/login")
    public ResponseResult login(@RequestBody SysUserVo sysUserVo){
        return getResult(sysUserService.login(sysUserVo));
    }

    private SysUser currentUser() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        String username = principal == null ? null : JwtUtil.getUsername(principal.toString());
        SysUser user = username == null ? null : sysUserService.findByName(username);
        if (user == null) throw new AuthenticationException("请先登录");
        return user;
    }

    private SysUser requireAdministrator() {
        SysUser actor = currentUser();
        if (!Long.valueOf(1L).equals(actor.getRoleId())) {
            throw new AuthenticationException("无权管理其他用户");
        }
        return actor;
    }

    private SysUser requireOwnerOrAdministrator(Long userId) {
        SysUser actor = currentUser();
        if (!Long.valueOf(1L).equals(actor.getRoleId()) && !actor.getUserId().equals(userId)) {
            throw new AuthenticationException("无权访问其他用户资料");
        }
        return actor;
    }
}
