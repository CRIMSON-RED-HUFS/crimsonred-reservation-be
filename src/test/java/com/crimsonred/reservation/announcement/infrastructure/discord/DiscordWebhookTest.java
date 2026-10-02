package com.crimsonred.reservation.announcement.infrastructure.discord;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiscordWebhookTest {
  @Test
  void multipartWaitAndMentionSuppression() throws Exception {
    var body = new AtomicReference<String>();
    var query = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/webhook",
        exchange -> {
          query.set(exchange.getRequestURI().getQuery());
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = "{\"id\":\"12345\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var hook =
          new DiscordWebhook(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook",
              JsonMapper.builder().build());
      assertEquals("SENT", hook.send("주간 예약", "합주 @everyone").status());
      assertEquals("wait=true", query.get());
      assertTrue(body.get().contains("\"allowed_mentions\":{\"parse\":[]}"));
      assertTrue(body.get().contains("weekly-reservations.txt"));
      assertTrue(body.get().contains("합주 @everyone"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failuresAndRateLimit() throws Exception {
    var code = new AtomicInteger(429);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] response = "{\"retry_after\":75}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(code.get(), response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var hook =
          new DiscordWebhook(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/",
              JsonMapper.builder().build());
      assertEquals(75, hook.send("공지", "현황").retrySeconds());
      code.set(400);
      assertEquals(0, hook.send("공지", "현황").retrySeconds());
      code.set(500);
      assertEquals("FAILED", hook.send("공지", "현황").status());
      code.set(200);
      assertEquals(
          "UNKNOWN", hook.send("공지", "현황").status()); // Successful HTTP without a message ID.
    } finally {
      server.stop(0);
    }
    assertEquals(
        "FAILED", new DiscordWebhook("", JsonMapper.builder().build()).send("공지", "현황").status());
  }

  @Test
  void lostResponseIsUnknown() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.close();
        });
    server.start();
    try {
      var hook =
          new DiscordWebhook(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/",
              JsonMapper.builder().build());
      assertEquals("UNKNOWN", hook.send("공지", "현황").status());
    } finally {
      server.stop(0);
    }
  }
}
