package com.example.paperqqbot.config;

public record WebSettings(boolean enabled, String host, int port, String accessToken) {
   public static WebSettings fallback() {
      return new WebSettings(true, "127.0.0.1", 8765, "");
   }
}
