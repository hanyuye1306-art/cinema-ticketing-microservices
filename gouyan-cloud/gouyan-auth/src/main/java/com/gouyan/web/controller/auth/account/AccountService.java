package com.gouyan.web.controller.auth.account;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.mapper.SysUserMapper;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import org.apache.shiro.authc.AuthenticationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Single-auth-instance, bounded, expiring challenges; codes never reach logs or API responses. */
@Service
public class AccountService {
    private static final long TTL = 10 * 60 * 1000L;
    private static final long COOLDOWN = 60 * 1000L;
    private static final int CAPACITY = 10000;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private final SysUserMapper mapper;
    private final SysUserServiceImpl users;
    private final CaptchaService captcha;
    private final JavaMailSender mail;
    private final boolean mailEnabled;
    private final String from;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Map<String, Rate> rates = new HashMap<>();

    @Autowired
    public AccountService(SysUserMapper mapper, SysUserServiceImpl users, CaptchaService captcha,
                          ObjectProvider<JavaMailSender> mail,
                          @Value("${gouyan.account.mail-enabled:false}") boolean enabled,
                          @Value("${gouyan.account.mail-from:}") String from) {
        this(mapper, users, captcha, mail.getIfAvailable(), enabled, from, Clock.systemUTC());
    }

    AccountService(SysUserMapper mapper, SysUserServiceImpl users, CaptchaService captcha,
                   JavaMailSender mail, boolean enabled, String from, Clock clock) {
        this.mapper = mapper;
        this.users = users;
        this.captcha = captcha;
        this.mail = mail;
        this.mailEnabled = enabled;
        this.from = from;
        this.clock = clock;
    }

    public synchronized void register(AccountRequests.Register request, String address) {
        validatePasswords(request.getPassword(), request.getConfirmPassword());
        verifyCaptcha(request.getCaptchaVerification());
        limit("register:" + address, 10, clock.millis());
        SysUser user = new SysUser();
        user.setUserName(request.getUserName().trim());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail().trim());
        user.setSex(request.getSex());
        user.setRoleId(3L);
        user.setUserPicture("[]");
        user.setInformation("{}");
        if (users.add(user) != 1) {
            throw new AuthenticationException("注册失败，请稍后重试");
        }
    }

    public synchronized void sendCode(AccountRequests.SendCode request, String address) {
        if (!mailEnabled || mail == null || !StringUtils.hasText(from)) {
            throw new AuthenticationException("找回密码邮件服务尚未配置，请联系管理员");
        }
        verifyCaptcha(request.getCaptchaVerification());
        long now = clock.millis();
        clean(now);
        limit("send-ip:" + address, 20, now);
        // Username-based rate limiting also covers nonexistent accounts and mismatched emails.
        String key = key(request.getUserName());
        limit("send-user:" + key, 5, now);
        Rate cooldown = rates.get("cooldown:" + key);
        if (cooldown != null && now - cooldown.started < COOLDOWN) {
            throw new AuthenticationException("请60秒后再获取验证码");
        }
        ensureCapacity();
        rates.put("cooldown:" + key, new Rate(now));
        SysUser user = mapper.findByName(request.getUserName().trim());
        if (user == null || !emailMatches(user.getEmail(), request.getEmail())) {
            return; // Controller returns the same response without disclosing account existence.
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        Challenge pending = new Challenge(user.getUserId(), user.getEmail(), nonce,
                digest(code, nonce), now + TTL);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("狗眼电影 - 找回密码验证码");
        message.setText("您正在申请重置狗眼电影账号密码。\n验证码：" + code
                + "\n10分钟内有效，请勿向他人提供。若非本人操作，请忽略此邮件。");
        try {
            mail.send(message);
        } catch (MailException ex) {
            throw new AuthenticationException("邮件发送失败，请稍后重试或联系管理员");
        }
        // Only invalidate the old code after the replacement has actually been sent.
        challenges.put(key, pending);
    }

    public synchronized void reset(AccountRequests.Reset request, String address) {
        validatePasswords(request.getPassword(), request.getConfirmPassword());
        long now = clock.millis();
        clean(now);
        limit("reset-ip:" + address, 60, now);
        String key = key(request.getUserName());
        Challenge challenge = challenges.get(key);
        if (challenge == null) {
            throw invalidCode();
        }
        challenge.attempts++;
        boolean valid = emailMatches(challenge.email, request.getEmail())
                && MessageDigest.isEqual(challenge.hash, digest(request.getCode(), challenge.nonce));
        if (!valid || challenge.attempts > 5) {
            if (challenge.attempts >= 5) challenges.remove(key);
            throw invalidCode();
        }
        // Consume the code once; update only the password for the bound ID AND stored email.
        challenges.remove(key);
        if (mapper.resetPassword(challenge.userId, ENCODER.encode(request.getPassword()), challenge.email) != 1) {
            throw invalidCode();
        }
    }

    private void verifyCaptcha(String verification) {
        if (!StringUtils.hasText(verification)) {
            throw new AuthenticationException("请先完成图形验证码");
        }
        CaptchaVO value = new CaptchaVO();
        value.setCaptchaType("blockPuzzle");
        value.setCaptchaVerification(verification);
        ResponseModel result = captcha.verification(value);
        if (result == null || !result.isSuccess()) {
            throw new AuthenticationException("图形验证码无效或已过期，请重新验证");
        }
    }

    private void validatePasswords(String password, String confirmation) {
        if (password == null || password.length() < 6 || password.length() > 16
                || !password.equals(confirmation)) {
            throw new AuthenticationException("密码须为6到16个字符，且两次输入一致");
        }
    }

    private void clean(long now) {
        challenges.entrySet().removeIf(entry -> entry.getValue().expires <= now);
        rates.entrySet().removeIf(entry -> now - entry.getValue().started >= TTL);
    }

    private void ensureCapacity() {
        if (challenges.size() >= CAPACITY || rates.size() >= CAPACITY) {
            throw new AuthenticationException("请求较多，请稍后重试");
        }
    }

    private void limit(String key, int maximum, long now) {
        clean(now);
        Rate rate = rates.get(key);
        if (rate == null) {
            ensureCapacity();
            rates.put(key, rate = new Rate(now));
        }
        if (++rate.count > maximum) {
            throw new AuthenticationException("操作过于频繁，请稍后重试");
        }
    }

    private String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    private boolean emailMatches(String stored, String input) {
        return StringUtils.hasText(stored) && input != null && stored.equalsIgnoreCase(input.trim());
    }
    private AuthenticationException invalidCode() {
        return new AuthenticationException("验证码无效或已过期，请重新获取");
    }
    private byte[] digest(String code, byte[] nonce) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(nonce);
            return hash.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static class Rate {
        final long started;
        int count;
        Rate(long started) { this.started = started; }
    }
    private static class Challenge {
        final Long userId;
        final String email;
        final byte[] nonce;
        final byte[] hash;
        final long expires;
        int attempts;
        Challenge(Long userId, String email, byte[] nonce, byte[] hash, long expires) {
            this.userId = userId;
            this.email = email;
            this.nonce = nonce;
            this.hash = hash;
            this.expires = expires;
        }
    }
}
