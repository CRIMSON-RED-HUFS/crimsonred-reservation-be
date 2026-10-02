package com.crimsonred.reservation.auth.infrastructure.redis;

import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.auth.application.port.AuthRateLimiter;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimiter implements AuthRateLimiter {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> INCREMENT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1])"
              + " end; return n",
          Long.class);

  public RateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private void check(String key, int seconds, int limit) {
    Long count =
        redis.execute(INCREMENT, List.of("crimsonred:rate:" + key), String.valueOf(seconds));
    if (count == null)
      throw new BusinessException(Type.UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "잠시 후 다시 시도해주세요.");
    if (count > limit)
      throw new BusinessException(Type.RATE_LIMITED, "RATE_LIMITED", "요청이 많습니다. 잠시 후 다시 시도해주세요.");
  }

  public void login(String ip, String email) {
    check("login:ip:" + AuthService.digest(ip), 900, 50);
    check("login:email:" + AuthService.digest(email), 900, 10);
  }

  public void mail(String ip, String email) {
    check("mail:ip:" + AuthService.digest(ip), 3600, 10);
    check("mail:email:" + AuthService.digest(email), 3600, 3);
  }
}
