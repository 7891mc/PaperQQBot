package com.example.paperqqbot.config;

public record BotSettings(String httpUrl, String accessToken, int connectTimeoutSec, int readTimeoutSec, boolean debug, int retry, long retryIntervalMs) {
   public String authHeader() {
      return this.accessToken != null && !this.accessToken.isBlank() ? "Bearer " + this.accessToken.trim() : null;
   }

   public boolean hasUrl() {
      return this.httpUrl != null && !this.httpUrl.isBlank();
   }
}
