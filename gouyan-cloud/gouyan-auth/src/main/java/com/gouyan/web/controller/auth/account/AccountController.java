package com.gouyan.web.controller.auth.account;

import com.gouyan.common.response.ResponseResult;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/sysUser")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/register")
    public ResponseResult register(@Valid @RequestBody AccountRequests.Register request, HttpServletRequest http) {
        accounts.register(request, http.getRemoteAddr());
        return ResponseResult.success("注册成功，请登录");
    }

    @PostMapping("/password/code")
    public ResponseResult sendCode(@Valid @RequestBody AccountRequests.SendCode request, HttpServletRequest http) {
        accounts.sendCode(request, http.getRemoteAddr());
        return ResponseResult.success("若用户名与绑定邮箱一致，验证码将发送至该邮箱，请查收（含垃圾邮件）");
    }

    @PostMapping("/password/reset")
    public ResponseResult reset(@Valid @RequestBody AccountRequests.Reset request, HttpServletRequest http) {
        accounts.reset(request, http.getRemoteAddr());
        return ResponseResult.success("密码已重置，请使用新密码登录");
    }
}
