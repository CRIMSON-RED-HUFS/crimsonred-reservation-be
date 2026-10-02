package com.crimsonred.reservation.auth.infrastructure.mail;

import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.auth.application.port.VerificationEmailSender;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class VerificationMail implements VerificationEmailSender {
  private final JavaMailSender sender;
  private final String baseUrl, from;
  private static final Logger log = LoggerFactory.getLogger(VerificationMail.class);

  public VerificationMail(
      JavaMailSender sender,
      @Value("${app.base-url}") String baseUrl,
      @Value("${app.mail-from}") String from) {
    this.sender = sender;
    this.baseUrl = baseUrl;
    this.from = from;
  }

  public void send(AuthService.MailPayload payload) {
    if (payload == null) return;
    String url = baseUrl.replaceAll("/+$", "") + "/verify?token=" + payload.token();
    try {
      var message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(payload.email());
      helper.setSubject("[크림슨레드] HUFS 이메일 인증");
      helper.setText(
          "이메일 인증 버튼을 눌러 인증을 완료해주세요. 인증은 30분 이내에 완료해주세요.\n"
              + url + "\n요청하지 않았다면 이 메일을 무시해주세요.",
          html(payload, url));
      sender.send(message);
    } catch (MailException | MessagingException e) {
      log.warn("Verification email delivery failed; user may request a new link.");
    }
  }

  private String html(AuthService.MailPayload payload, String url) {
    String link = HtmlUtils.htmlEscape(url);
    return """
        <!doctype html>
        <html lang="ko">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>HUFS 이메일 인증</title></head>
        <body style="margin:0;background:#fff;color:#171717;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" bgcolor="#ffffff"><tr><td align="center" style="padding:40px 24px;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:432px;table-layout:fixed;font-family:Pretendard,Arial,'Malgun Gothic',sans-serif;font-size:14px;line-height:1.6;color:#171717;">
          <tr><td style="padding-bottom:24px;border-bottom:1px solid #e5e5e5;">
            <p style="margin:0;font-size:20px;font-weight:800;letter-spacing:0;">CRIMSONRED</p>
            <p style="margin:4px 0 0;font-size:12px;color:#737373;">동아리방 예약</p>
          </td></tr>
          <tr><td align="left" style="padding-top:32px;">
            <h1 style="margin:0;font-size:26px;line-height:1.3;letter-spacing:-0.02em;font-weight:700;">HUFS 이메일 인증</h1>
            <p style="margin:12px 0 0;color:#737373;font-size:13px;line-height:1.7;word-break:break-word;">안녕하세요, %s님.<br>크림슨레드 동아리방 이용을 위해<br>아래 버튼을 눌러 이메일 인증을 완료해주세요.</p>
            <p style="margin:24px 0 0;padding:16px;background:#f5f5f5;border:1px solid #e5e5e5;border-radius:12px;font-size:14px;font-weight:600;word-break:break-all;">%s</p>
            <p style="margin:12px 0 0;font-size:12px;color:#737373;">인증은 메일 발송 후 30분 이내에 완료해주세요.</p>
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-top:24px;"><tr><td align="center" bgcolor="#171717" style="border-radius:999px;">
              <a href="%s" style="display:block;padding:14px 24px;border-radius:999px;background:#171717;color:#fff;font-size:14px;line-height:20px;text-align:center;text-decoration:none;font-weight:600;">이메일 주소 인증하기</a>
            </td></tr></table>
            <p style="margin:32px 0 0;border-top:1px solid #e5e5e5;padding-top:24px;color:#737373;font-size:12px;line-height:1.7;">버튼이 열리지 않으면 아래 주소를 복사해 브라우저에서 접속해주세요.</p>
            <p style="margin:8px 0 0;word-break:break-all;overflow-wrap:anywhere;font-size:12px;line-height:1.7;"><a href="%s" style="color:#737373;text-decoration:underline;">%s</a></p>
          </td></tr>
        </table></td></tr></table></body></html>
        """.formatted(HtmlUtils.htmlEscape(payload.name()), HtmlUtils.htmlEscape(payload.email()), link, link, link);
  }
}
