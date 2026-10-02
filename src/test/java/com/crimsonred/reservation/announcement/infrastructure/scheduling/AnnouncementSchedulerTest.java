package com.crimsonred.reservation.announcement.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.announcement.application.AnnouncementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AnnouncementSchedulerTest {
  @Test
  void optionalNotificationFailureDoesNotBreakStartupOrLogPrivateDetails(CapturedOutput output) {
    var service = mock(AnnouncementService.class);
    doThrow(new IllegalStateException("private-webhook-url")).when(service).recoverAndPump();
    assertDoesNotThrow(() -> new AnnouncementScheduler(service).recover());
    assertTrue(output.getOut().contains("Announcement recovery failed"));
    assertFalse(output.getAll().contains("private-webhook-url"));
  }
}
