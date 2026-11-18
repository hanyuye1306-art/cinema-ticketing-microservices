package com.gouyan.web.controller.auth.account;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.mapper.SysUserMapper;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import org.apache.shiro.authc.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private SysUserMapper mapper;
    private SysUserServiceImpl users;
    private CaptchaService captcha;
    private JavaMailSender mail;
    private AccountService service;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        mapper = mock(SysUserMapper.class);
        users = mock(SysUserServiceImpl.class);
        captcha = mock(CaptchaService.class);
        mail = mock(JavaMailSender.class);
        clock = new MutableClock();
        service = new AccountService(mapper, users, captcha, mail, true, "sender@example.invalid", clock);
        when(captcha.verification(any(CaptchaVO.class))).thenReturn(ResponseModel.success());
        SysUser user = new SysUser();
        user.setUserId(42L); user.setUserName("test-user"); user.setEmail("owner@example.invalid");
        when(mapper.findByName("test-user")).thenReturn(user);
        when(mapper.resetPassword(eq(42L), anyString(), eq("owner@example.invalid"))).thenReturn(1);
    }

    @Test
    void publicRegistrationAssignsOnlyRegularRoleAndUsesVerifiedCaptcha() {
        AccountRequests.Register request = new AccountRequests.Register();
        request.setUserName("new-user"); request.setEmail("new@example.invalid");
        request.setPassword("new-secret"); request.setConfirmPassword("new-secret");
        request.setSex(false); request.setCaptchaVerification("proof");
        when(users.add(any(SysUser.class))).thenReturn(1);
        service.register(request, "client");
        ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
        verify(users).add(saved.capture());
        assertEquals(3L, saved.getValue().getRoleId());
        assertNull(saved.getValue().getUserId());
        assertEquals("[]", saved.getValue().getUserPicture());
        ArgumentCaptor<CaptchaVO> checked = ArgumentCaptor.forClass(CaptchaVO.class);
        verify(captcha).verification(checked.capture());
        assertEquals("proof", checked.getValue().getCaptchaVerification());
    }

    @Test
    void invalidCaptchaCannotSendEmail() {
        when(captcha.verification(any(CaptchaVO.class))).thenReturn(ResponseModel.errorMsg("invalid"));
        assertThrows(AuthenticationException.class, () -> service.sendCode(send(), "client"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationCannotBypassCaptcha() {
        AccountRequests.Register request = new AccountRequests.Register();
        request.setPassword("new-secret"); request.setConfirmPassword("new-secret");
        assertThrows(AuthenticationException.class, () -> service.register(request, "client"));
        verify(users, never()).add(any());
    }

    @Test
    void missingMailConfigurationDoesNotPretendToSend() {
        service = new AccountService(mapper, users, captcha, mail, false, "", clock);
        assertTrue(assertThrows(AuthenticationException.class, () -> service.sendCode(send(), "client"))
                .getMessage().contains("尚未配置"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void nonexistentAccountAndWrongEmailDoNotSendMessages() {
        AccountRequests.SendCode request = send(); request.setEmail("attacker@example.invalid");
        service.sendCode(request, "client");
        request.setUserName("missing-user"); service.sendCode(request, "client");
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetUsesBoundUserAndBcryptAndCodeCannotBeReplayed() {
        String code = issue();
        service.reset(reset(code), "client");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).resetPassword(eq(42L), hash.capture(), eq("owner@example.invalid"));
        assertTrue(new BCryptPasswordEncoder().matches("new-secret", hash.getValue()));
        assertThrows(AuthenticationException.class, () -> service.reset(reset(code), "client"));
        verify(mapper, times(1)).resetPassword(anyLong(), anyString(), anyString());
    }

    @Test
    void fiveIncorrectAttemptsInvalidateEvenTheCorrectCode() {
        String code = issue();
        String wrong = "000000".equals(code) ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthenticationException.class, () -> service.reset(reset(wrong), "client"));
        }
        assertThrows(AuthenticationException.class, () -> service.reset(reset(code), "client"));
        verify(mapper, never()).resetPassword(anyLong(), anyString(), anyString());
    }

    @Test
    void codeIsBoundToBothAccountAndEmail() {
        String code = issue();
        AccountRequests.Reset wrong = reset(code); wrong.setEmail("other@example.invalid");
        assertThrows(AuthenticationException.class, () -> service.reset(wrong, "client"));
        wrong.setEmail("owner@example.invalid"); wrong.setUserName("other-user");
        assertThrows(AuthenticationException.class, () -> service.reset(wrong, "client"));
        verify(mapper, never()).resetPassword(anyLong(), anyString(), anyString());
    }

    @Test
    void expiredCodeCannotChangePassword() {
        String code = issue(); clock.advance(600000);
        assertThrows(AuthenticationException.class, () -> service.reset(reset(code), "client"));
        verify(mapper, never()).resetPassword(anyLong(), anyString(), anyString());
    }

    @Test
    void resendHasCooldownAndReplacesPreviousCode() {
        String first = issue();
        assertThrows(AuthenticationException.class, () -> service.sendCode(send(), "client"));
        clock.advance(60000);
        String second = issue();
        if (!first.equals(second)) assertThrows(AuthenticationException.class, () -> service.reset(reset(first), "client"));
        service.reset(reset(second), "client");
    }

    @Test
    void smtpFailureDoesNotActivateUnsentChallenge() {
        doThrow(new MailSendException("offline")).when(mail).send(any(SimpleMailMessage.class));
        assertThrows(AuthenticationException.class, () -> service.sendCode(send(), "client"));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThrows(AuthenticationException.class, () -> service.reset(reset(extractCode(sent.getValue())), "client"));
        verify(mapper, never()).resetPassword(anyLong(), anyString(), anyString());
    }

    @Test
    void concurrentRedemptionChangesPasswordExactlyOnce() throws Exception {
        String code = issue(); ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> redeem = () -> { start.await(); try { service.reset(reset(code), "client"); return true; }
            catch (AuthenticationException expected) { return false; } };
        try {
            Future<Boolean> a = executor.submit(redeem), b = executor.submit(redeem); start.countDown();
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            verify(mapper, times(1)).resetPassword(anyLong(), anyString(), anyString());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void repeatedRequestsFromOneAddressAreLimitedEvenForUnknownAccounts() {
        for (int i = 0; i < 20; i++) {
            AccountRequests.SendCode request = send(); request.setUserName("missing-" + i);
            service.sendCode(request, "client");
        }
        AccountRequests.SendCode request = send(); request.setUserName("missing-21");
        assertThrows(AuthenticationException.class, () -> service.sendCode(request, "client"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    private String issue() {
        service.sendCode(send(), "client");
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        SimpleMailMessage message = sent.getAllValues().get(sent.getAllValues().size() - 1);
        assertArrayEquals(new String[]{"owner@example.invalid"}, message.getTo());
        return extractCode(message);
    }
    private String extractCode(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("验证码：(\\d{6})").matcher(message.getText());
        assertTrue(matcher.find()); return matcher.group(1);
    }
    private AccountRequests.SendCode send() {
        AccountRequests.SendCode request = new AccountRequests.SendCode();
        request.setUserName("test-user"); request.setEmail("owner@example.invalid"); request.setCaptchaVerification("proof");
        return request;
    }
    private AccountRequests.Reset reset(String code) {
        AccountRequests.Reset request = new AccountRequests.Reset();
        request.setUserName("test-user"); request.setEmail("owner@example.invalid"); request.setCode(code);
        request.setPassword("new-secret"); request.setConfirmPassword("new-secret"); return request;
    }
    private static class MutableClock extends Clock {
        long now = 1000000;
        void advance(long ms) { now += ms; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public long millis() { return now; }
    }
}
