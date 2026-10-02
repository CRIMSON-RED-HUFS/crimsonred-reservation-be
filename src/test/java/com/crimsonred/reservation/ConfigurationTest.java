package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfigurationTest {
  @Test
  void testUsesTestValuesAndCommonSettings() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active=test")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              var env = context.getEnvironment();
              assertEquals("WARN", env.getProperty("logging.level.root"));
              assertEquals("off", env.getProperty("spring.main.banner-mode"));
              assertEquals("false", env.getProperty("management.health.mail.enabled"));
              assertEquals("http://localhost:3000", env.getProperty("app.base-url"));
              assertEquals("crimsonred@hufs.ac.kr", env.getProperty("app.mail-from"));
              assertEquals("", env.getProperty("app.webhook-url"));
              assertEquals("false", env.getProperty("app.announcements-enabled"));
              assertEquals("0 0 23 * * SUN", env.getProperty("app.announcement-cron"));
              assertEquals("540", env.getProperty("app.booking.start-minute"));
              assertEquals("OFF", env.getProperty("logging.level.org.hibernate.orm.jdbc.error"));
              assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
            });
  }

  @Test
  void productionUsesDeploymentValuesAndCommonSettings() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.profiles.active=prod",
            "DB_URL=jdbc:mysql://prod-db/crimsonred",
            "DB_USERNAME=prod-user",
            "DB_PASSWORD=test-db-password",
            "REDIS_HOST=prod-redis",
            "REDIS_PASSWORD=test-redis-password",
            "SMTP_HOST=prod-mail",
            "APP_BASE_URL=https://reservation.example",
            "MAIL_FROM=band@example.com")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              var env = context.getEnvironment();
              assertEquals(
                  "jdbc:mysql://prod-db/crimsonred", env.getProperty("spring.datasource.url"));
              assertEquals("prod-user", env.getProperty("spring.datasource.username"));
              assertEquals("test-db-password", env.getProperty("spring.datasource.password"));
              assertEquals("prod-redis", env.getProperty("spring.data.redis.host"));
              assertEquals("test-redis-password", env.getProperty("spring.data.redis.password"));
              assertEquals("prod-mail", env.getProperty("spring.mail.host"));
              assertEquals("https://reservation.example", env.getProperty("app.base-url"));
              assertEquals("band@example.com", env.getProperty("app.mail-from"));
              assertEquals("0 0 23 * * SUN", env.getProperty("app.announcement-cron"));
              assertEquals("540", env.getProperty("app.booking.start-minute"));
              assertEquals("1350", env.getProperty("app.booking.end-minute"));
              assertTrue(env.getProperty("server.servlet.session.cookie.secure", Boolean.class));
              assertTrue(
                  env.getProperty(
                      "spring.mail.properties.mail.smtp.starttls.required", Boolean.class));
              assertTrue(env.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity", Boolean.class));
              assertEquals("5000", env.getProperty("spring.datasource.hikari.connection-timeout"));
              assertEquals("5000", env.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout"));
              assertEquals("5000", env.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout"));
              assertFalse(env.getProperty("spring.jpa.open-in-view", Boolean.class));
              assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
              assertEquals(
                  "TRANSACTION_READ_COMMITTED",
                  env.getProperty("spring.datasource.hikari.transaction-isolation"));
              assertEquals(
                  "UTC", env.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"));
            });
  }
}
