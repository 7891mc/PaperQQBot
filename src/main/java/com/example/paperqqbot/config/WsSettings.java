package com.example.paperqqbot.config;

public record WsSettings(boolean enabled, String url, String accessToken, long reconnectMs) {
   public static WsSettings fallback() {
      return new WsSettings(false, "", "", 5000L);
   }

   public String authHeader() {
      return this.accessToken != null && !this.accessToken.isBlank() ? "Bearer " + this.accessToken.trim() : null;
   }

   public boolean hasUrl() {
      return this.url != null && !this.url.isBlank();
   }
}
