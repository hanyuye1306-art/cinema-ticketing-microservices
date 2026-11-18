package com.gouyan.system.domin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;


@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
public class SysUser implements Serializable {
    public interface Create { }
    public interface Update { }
    private final static Long serialVersionUID = 1L;
    //用户id
    @NotNull(groups = Update.class, message = "缺少用户ID")
    private Long userId;
    //用户名
    @NotBlank(message = "用户名不能为空")
    private String userName;
    //密码
    @NotBlank(groups = Create.class, message = "密码不能为空")
    @Size(groups = Create.class, min = 6, max = 16, message = "密码长度为6到16个字符")
    @Pattern(groups = Update.class, regexp = "(?s)(?:|(?=.{6,16}$).*\\S.*)", message = "新密码须为6到16个字符，留空则保持原密码")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    //盐
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String salt;
    //邮箱
    @Email(message = "邮箱格式有误")
    private String email;
    //电话号码
    @Pattern(regexp = "^(?:1[3-9]\\d{9})?$", message = "电话号码格式有错")
    private String phoneNumber;
    //性别
    private Boolean sex;
    //用户头像
    private String userPicture;
    //用户对应的角色id，为简化操作，采用1对1
    private Long roleId;
    //用户信息
    private String information;

    //用户的角色
    private SysRole sysRole;
}
