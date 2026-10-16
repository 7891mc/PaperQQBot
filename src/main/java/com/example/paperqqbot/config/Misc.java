package com.example.paperqqbot.config;

public record Misc(boolean asyncShutdownPush, long shutdownPushWaitTicks, long startPushDelayTicks) {
   public static Misc fallback() {
      return new Misc(true, 60L, 40L);
   }
}
