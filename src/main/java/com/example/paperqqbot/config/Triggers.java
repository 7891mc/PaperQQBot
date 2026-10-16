package com.example.paperqqbot.config;

public record Triggers(
   boolean emptyOnZeroPlayers, boolean emptyOnlyOnTransition, boolean tpsWarningEnabled, double tpsWarningThreshold, long tpsWarningCooldownSeconds
) {
   public static Triggers fallback() {
      return new Triggers(true, true, true, 15.0, 300L);
   }
}
