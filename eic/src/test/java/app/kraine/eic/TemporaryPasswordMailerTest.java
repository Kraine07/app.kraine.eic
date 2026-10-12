package app.kraine.eic;


import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import app.kraine.eic.auth.TemporaryPasswordMailer;
import app.kraine.eic.exception.EmailDeliveryException;

import java.util.HashMap;
import java.util.Map;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class TemporaryPasswordMailerTest {
 
    /** Builds real MIME messages but sends nothing. */
    static class CapturingSender extends JavaMailSenderImpl {
        MimeMessage sent;
        RuntimeException failure;
 
        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            if (failure != null) {
                throw failure;
            }
            sent = mimeMessage;
        }
    }
 
    @Mock private ObjectProvider<JavaMailSender> mailSender;
 
    private final CapturingSender sender = new CapturingSender();
    private TemporaryPasswordMailer mailer;
 
    @BeforeEach
    void setUp() {
        mailer = new TemporaryPasswordMailer(mailSender);
        ReflectionTestUtils.setField(mailer, "from", "no-reply@example.com");
    }
 
    /** Collects the text/plain and text/html parts, however deeply the multipart is nested. */
    private static void collect(Part part, Map<String, String> bodies) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), bodies);
            }
        } else if (part.isMimeType("text/plain")) {
            bodies.put("plain", (String) part.getContent());
        } else if (part.isMimeType("text/html")) {
            bodies.put("html", (String) part.getContent());
        }
    }
 
    private Map<String, String> send(String firstName, String password) throws Exception {
        when(mailSender.getIfAvailable()).thenReturn(sender);
        mailer.sendTemporaryPassword("ana@example.com", firstName, password);
        Map<String, String> bodies = new HashMap<>();
        collect(sender.sent, bodies);
        return bodies;
    }
 
    @Test
    void sendsAnHtmlEmailWithAPlainTextAlternative() throws Exception {
        Map<String, String> bodies = send("Ana", "Temp#Pass12");
 
        assertEquals("Your Equipment Inventory account", sender.sent.getSubject());
        assertEquals("ana@example.com", sender.sent.getAllRecipients()[0].toString());
        assertTrue(bodies.get("html").contains("<html"));
        assertTrue(bodies.get("html").contains("Hello Ana,"));
        assertTrue(bodies.get("html").contains("Temp#Pass12"));
        assertTrue(bodies.get("plain").contains("Temp#Pass12"));
        assertFalse(bodies.get("plain").contains("<"));
    }
 
    @Test
    void escapesHtmlCharactersInTheNameAndPassword() throws Exception {
        Map<String, String> bodies = send("<b>Ana</b>", "a&b<c>x");
 
        assertTrue(bodies.get("html").contains("&lt;b&gt;Ana&lt;/b&gt;"));
        assertTrue(bodies.get("html").contains("a&amp;b&lt;c&gt;x"));
        assertFalse(bodies.get("html").contains("<b>Ana</b>"));
        assertTrue(bodies.get("plain").contains("a&b<c>x")); // the plain-text part is not HTML
    }
 
    @Test
    void failsWhenMailIsNotConfigured() {
        when(mailSender.getIfAvailable()).thenReturn(null);
 
        assertThrows(EmailDeliveryException.class,
                () -> mailer.sendTemporaryPassword("ana@example.com", "Ana", "Temp#Pass12"));
    }
 
    @Test
    void wrapsAMailServerFailure() {
        when(mailSender.getIfAvailable()).thenReturn(sender);
        sender.failure = new MailSendException("smtp down");
 
        EmailDeliveryException ex = assertThrows(EmailDeliveryException.class,
                () -> mailer.sendTemporaryPassword("ana@example.com", "Ana", "Temp#Pass12"));
 
        assertEquals("The email could not be sent.", ex.getMessage());
    }
}
 