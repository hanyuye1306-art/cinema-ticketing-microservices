import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.Properties;

/** Checks TLS and SMTP authentication only; does not send a message. */
public final class SmtpConnectionCheck {
    public static void main(String[] args) {
        // 由 configure-mail.ps1 调用，探测 QQ SMTP 的隐式 TLS 握手与账户认证。
        // 服务器身份校验用于核对证书与主机名。连接、读取与写入分别设置 10 秒超时。
        // 凭据由环境变量传入。诊断时依据退出码和错误类别定位，避免输出凭据。
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        // try-with-resources 负责关闭传输连接。connect 成功即完成本次探测。
        // 退出码 2 表示认证失败，1 表示其他邮件连接异常，正常结束为 0。
        try (Transport smtp = Session.getInstance(properties).getTransport("smtp")) {
            smtp.connect("smtp.qq.com", 465, System.getenv("SMTP_USERNAME"), System.getenv("SMTP_PASSWORD"));
            System.out.println("SMTP TLS and authentication passed; no message was sent.");
        } catch (AuthenticationFailedException ex) {
            System.err.println("SMTP authentication failed. Use a QQ SMTP authorization code, not the mailbox password.");
            System.exit(2);
        } catch (MessagingException ex) {
            System.err.println("SMTP connection failed. Check network, TLS and QQ SMTP service availability.");
            System.exit(1);
        }
    }
}
