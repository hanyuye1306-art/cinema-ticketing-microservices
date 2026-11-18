package com.gouyan.web.controller.auth.account;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.mapper.SysUserMapper;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SMTP transport into a loopback-only disposable mailbox, never an external address. */
class SmtpDeliveryTest {
    @Test
    void utf8EmailDeliveredOverSmtpCanBeUsedToResetPassword() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(10000);
            Future<String> received = executor.submit(() -> receive(server));
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1"); sender.setPort(server.getLocalPort()); sender.setDefaultEncoding("UTF-8");
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
            SysUserMapper mapper = mock(SysUserMapper.class);
            SysUser user = new SysUser(); user.setUserId(42L); user.setEmail("owner@example.invalid");
            when(mapper.findByName("test-user")).thenReturn(user);
            when(mapper.resetPassword(eq(42L), anyString(), eq(user.getEmail()))).thenReturn(1);
            CaptchaService captcha = mock(CaptchaService.class);
            when(captcha.verification(any(CaptchaVO.class))).thenReturn(ResponseModel.success());
            AccountService service = new AccountService(mapper, mock(SysUserServiceImpl.class), captcha,
                    sender, true, "sender@example.invalid", Clock.systemUTC());
            AccountRequests.SendCode request = new AccountRequests.SendCode(); request.setUserName("test-user");
            request.setEmail(user.getEmail()); request.setCaptchaVerification("test-proof");
            service.sendCode(request, "local-test");
            MimeMessage email = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(received.get(10, TimeUnit.SECONDS).getBytes(StandardCharsets.US_ASCII)));
            assertEquals("狗眼电影 - 找回密码验证码", email.getSubject());
            String body = email.getContent().toString();
            Matcher matcher = Pattern.compile("验证码：(\\d{6})").matcher(body);
            assertTrue(matcher.find());
            AccountRequests.Reset reset = new AccountRequests.Reset(); reset.setUserName("test-user");
            reset.setEmail(user.getEmail()); reset.setCode(matcher.group(1));
            reset.setPassword("new-secret"); reset.setConfirmPassword("new-secret");
            service.reset(reset, "local-test");
            verify(mapper).resetPassword(eq(42L), anyString(), eq(user.getEmail()));
        } finally { executor.shutdownNow(); }
    }

    private String receive(ServerSocket server) throws IOException {
        try (Socket client = server.accept()) {
            client.setSoTimeout(10000);
            BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.US_ASCII), true);
            output.print("220 localhost test SMTP\r\n"); output.flush();
            StringBuilder message = new StringBuilder();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("DATA")) {
                    output.print("354 Send message\r\n"); output.flush();
                    while ((line = input.readLine()) != null && !line.equals(".")) message.append(line).append("\r\n");
                    output.print("250 Accepted\r\n");
                } else if (line.startsWith("QUIT")) {
                    output.print("221 Bye\r\n"); output.flush(); break;
                } else { output.print("250 OK\r\n"); }
                output.flush();
            }
            return message.toString();
        }
    }
}
