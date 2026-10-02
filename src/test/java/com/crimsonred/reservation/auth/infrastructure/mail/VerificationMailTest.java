package com.crimsonred.reservation.auth.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.auth.application.AuthService;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class VerificationMailTest {
  @Test
  void sendsAccessibleHtmlButtonAndPlainTextWithEscapedPersonalDetails() throws Exception {
    var sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    var mail = new VerificationMail(sender, "http://localhost:3000/", "noreply@hufs.ac.kr");
    mail.send(new AuthService.MailPayload("a&b@hufs.ac.kr", "<신입>", "token"));
    var captured = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captured.capture());
    var message = captured.getValue();
    message.saveChanges();
    assertEquals("[크림슨레드] HUFS 이메일 인증", message.getSubject());
    var mixed = (Multipart) message.getContent();
    var related = (Multipart) mixed.getBodyPart(0).getContent();
    var alternative = (Multipart) related.getBodyPart(0).getContent();
    String plain = (String) alternative.getBodyPart(0).getContent();
    String html = (String) alternative.getBodyPart(1).getContent();
    assertTrue(plain.contains("http://localhost:3000/verify?token=token"));
    assertTrue(html.contains("href=\"http://localhost:3000/verify?token=token\""));
    assertTrue(html.contains("이메일 주소 인증하기"));
    assertTrue(html.contains("30분"));
    assertTrue(html.contains("&lt;신입&gt;"));
    assertTrue(html.contains("a&amp;b@hufs.ac.kr"));
    assertFalse(html.contains("<신입>"));
  }
}
