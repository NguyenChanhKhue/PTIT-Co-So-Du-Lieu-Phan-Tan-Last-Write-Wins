package com.example.distributed_last_write_wins.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ClockSkewService {
  @Getter
  @Setter
  @Value("${clock.skew-seconds:0}")
  private long skewSeconds;

  public long getCurrentTimestamp() {
    long skewedTime = System.currentTimeMillis() + (skewSeconds * 1000);
    log.debug("Clock: real={}, skew={}s, skewed={}",
        System.currentTimeMillis(), skewSeconds, skewedTime);
    return skewedTime;
  }

  public long getRealTimestamp() {
    return System.currentTimeMillis();
  }

  /**
   * Get human-readable description of clock skew
   */
  public String getSkewDescription() {
    if (skewSeconds > 0) {
      return "Fast (+" + skewSeconds + " seconds)";
    } else if (skewSeconds < 0) {
      return "Slow (" + skewSeconds + " seconds)";
    } else {
      return "Normal (accurate)";
    }
  }

  /**
   * Get danger warning based on skew
   */
  public String getDangerWarning() {
    if (Math.abs(skewSeconds) > 10) {
      return " HIGH RISK: Updates may be incorrectly discarded!";
    } else if (Math.abs(skewSeconds) > 0) {
      return " Clock skew detected: LWW may produce incorrect results";
    }
    return "✓ Clock is synchronized";
  }
}
