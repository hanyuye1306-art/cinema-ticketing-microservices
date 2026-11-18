package com.gouyan.web.controller.auth.account;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.*;

/** Public account APIs deliberately do not accept IDs, roles or password hashes. */
public final class AccountRequests {
    private AccountRequests() { }

    @Getter @Setter
    public static class Identity {
        @NotBlank(message = "请输入用户名")
        @Size(min = 2, max = 20, message = "用户名长度为2到20个字符")
        private String userName;
        @NotBlank(message = "请输入注册时绑定的邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱地址过长")
        private String email;
    }

    @Getter @Setter
    public static class Register extends Identity {
        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 16, message = "密码长度为6到16个字符")
        private String password;
        @NotBlank(message = "请确认密码")
        private String confirmPassword;
        @NotBlank(message = "请完成图形验证码")
        @Size(max = 2048)
        private String captchaVerification;
        @NotNull(message = "请选择性别")
        private Boolean sex;
    }

    @Getter @Setter
    public static class SendCode extends Identity {
        @NotBlank(message = "请完成图形验证码")
        @Size(max = 2048)
        private String captchaVerification;
    }

    @Getter @Setter
    public static class Reset extends Identity {
        @NotBlank(message = "请输入邮箱验证码")
        @Pattern(regexp = "[0-9]{6}", message = "邮箱验证码为6位数字")
        private String code;
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 16, message = "密码长度为6到16个字符")
        private String password;
        @NotBlank(message = "请确认新密码")
        private String confirmPassword;
    }
}
