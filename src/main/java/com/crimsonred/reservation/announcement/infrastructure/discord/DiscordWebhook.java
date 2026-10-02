package com.crimsonred.reservation.announcement.infrastructure.discord;

import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DiscordWebhook implements AnnouncementSender {
  private final String url;
  private final ObjectMapper json;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public DiscordWebhook(@Value("${app.webhook-url}") String url, ObjectMapper json) {
    this.url = url;
    this.json = json;
  }

  public Result send(String content, String schedule) {
    if (url.isBlank()) return new Result("FAILED", null, 0, "Webhook 설정이 필요합니다.");
    try {
      String boundary = "crimsonred-" + UUID.randomUUID();
      String payload =
          json.writeValueAsString(
              Map.of(
                  "content",
                  content,
                  "allowed_mentions",
                  Map.of("parse", List.of()),
                  "attachments",
                  List.of(Map.of("id", 0, "filename", "weekly-reservations.txt"))));
      var body = new ByteArrayOutputStream();
      body.write(
          ("--"
                  + boundary
                  + "\r\n"
                  + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                  + "Content-Type: application/json\r\n\r\n"
                  + payload
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
      body.write(
          ("--"
                  + boundary
                  + "\r\n"
                  + "Content-Disposition: form-data; name=\"files[0]\";"
                  + " filename=\"weekly-reservations.txt\"\r\n"
                  + "Content-Type: text/plain; charset=utf-8\r\n\r\n"
                  + schedule
                  + "\r\n--"
                  + boundary
                  + "--\r\n")
              .getBytes(StandardCharsets.UTF_8));
      URI uri = URI.create(url + (url.contains("?") ? "&" : "?") + "wait=true");
      var req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
              .build();
      var response = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        String id;
        try {
          id = json.readTree(response.body()).path("id").asString("");
        } catch (Exception e) {
          return new Result("UNKNOWN", null, 0, "발송 응답을 확인할 수 없습니다.");
        }
        return id.isBlank()
            ? new Result("UNKNOWN", null, 0, "발송 응답을 확인할 수 없습니다.")
            : new Result("SENT", id, 0, null);
      }
      if (response.statusCode() == 429) {
        int delay = 60;
        try {
          delay =
              Math.max(
                  1,
                  (int) Math.ceil(json.readTree(response.body()).path("retry_after").asDouble(60)));
        } catch (Exception ignored) {
        }
        return new Result("FAILED", null, delay, "Discord 요청 제한");
      }
      return new Result(
          "FAILED",
          null,
          response.statusCode() >= 500 ? 30 : 0,
          "Discord HTTP " + response.statusCode());
    } catch (HttpTimeoutException e) {
      return new Result("UNKNOWN", null, 0, "응답 시간 초과: 채널 확인 후 재발송하세요.");
    } catch (ConnectException e) {
      return new Result("FAILED", null, 30, "Discord 연결 실패");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Result("UNKNOWN", null, 0, "발송 중단: 채널 확인이 필요합니다.");
    } catch (IOException e) {
      return new Result("UNKNOWN", null, 0, "전송 결과 불명: 채널 확인이 필요합니다.");
    } catch (Exception e) {
      return new Result("FAILED", null, 0, "Webhook 설정 또는 응답 형식을 확인해주세요.");
    }
  }
}
