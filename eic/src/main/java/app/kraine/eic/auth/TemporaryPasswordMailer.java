package app.kraine.eic.auth;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import app.kraine.eic.exception.EmailDeliveryException;

import java.nio.charset.StandardCharsets;
 
/**
 * UC-02 step 9 and UC-05 step 9: emails a temporary password as an HTML message with a plain-text
 * alternative. The password is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryPasswordMailer {
 
    private static final String SUBJECT = "Your Equipment Inventory account";
 
    /**
     * Table layout and inline styles, because many email programs ignore style sheets and modern CSS.
     * The {{placeholders}} are replaced with HTML-escaped values.
     */
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Your Equipment Inventory account</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f6f7f9;">
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f6f7f9;">
                <tr>
                  <td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="560" cellspacing="0" cellpadding="0" border="0" style="width:560px; max-width:100%; background-color:#ffffff; border:1px solid #d5d9df; border-radius:6px;">
                      <tr>
                        <td style="padding:20px 28px; border-bottom:1px solid #d5d9df; font-family:Arial,Helvetica,sans-serif; font-size:18px; font-weight:bold; color:#1b1f24;">
                          Equipment Inventory
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:28px; font-family:Arial,Helvetica,sans-serif; font-size:16px; line-height:24px; color:#1b1f24;">
                          <p style="margin:0 0 16px;">Hello {{firstName}},</p>
                          <p style="margin:0 0 16px;">Your Equipment Inventory account is now ready.</p>
                          <p style="margin:0 0 8px;">Sign in with your email address and this temporary password:</p>
                          <p style="margin:0 0 20px; padding:14px 16px; background-color:#eef1f4; border:1px solid #d5d9df; border-radius:4px; font-family:Consolas,'Courier New',monospace; font-size:20px; color:#1b1f24;">{{password}}</p>
                          <p style="margin:0;">Please change it after you sign in.</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:16px 28px; border-top:1px solid #d5d9df; font-family:Arial,Helvetica,sans-serif; font-size:10px; line-height:20px; color:#4a5360;">
                          You received this email because an Administrator set up  or reactivated an account for this address.
                          If you were not expecting it, you can ignore this message.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
 
    private final ObjectProvider<JavaMailSender> mailSender;
 
    @Value("${app.mail.from:no-reply@equipment-inventory.local}")
    private String from;
 
    public void sendTemporaryPassword(String to, String firstName, String temporaryPassword) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new EmailDeliveryException("Email is not configured on the server (spring.mail.host).");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            // multipart=true lets the message carry both an HTML body and a plain-text alternative
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(plainText(firstName, temporaryPassword), htmlBody(firstName, temporaryPassword));
            sender.send(message);
        } catch (MessagingException | MailException ex) {
            log.error("Could not send the temporary password email to {}", to, ex);
            throw new EmailDeliveryException("The email could not be sent."+ ex.getMessage());
        }
    }
 
    /** Shown by email programs that cannot display HTML. */
    static String plainText(String firstName, String temporaryPassword) {
        return "Hello " + firstName + ",\n\n"
                + "An account has been set up for you in Equipment Inventory.\n\n"
                + "Sign in with your email address and this temporary password:\n\n"
                + "    " + temporaryPassword + "\n\n"
                + "Please change it after you sign in.";
    }
 
    /** The name and password go into HTML, so characters such as & and < must be escaped. */
    static String htmlBody(String firstName, String temporaryPassword) {
        return HTML_TEMPLATE
                .replace("{{firstName}}", HtmlUtils.htmlEscape(firstName))
                .replace("{{password}}", HtmlUtils.htmlEscape(temporaryPassword));
    }
}
 