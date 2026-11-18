import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.Properties;

/** Checks TLS and SMTP authentication only; does not send a message. */
public final class SmtpConnectionCheck {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
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
