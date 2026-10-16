package com.example.paperqqbot.config;

public record OfficialBotSettings(String appId, String secret) {
   public static OfficialBotSettings fallback() {
      return new OfficialBotSettings("", "");
   }

   public boolean ready() {
      return this.appId != null && !this.appId.isBlank() && this.secret != null && !this.secret.isBlank();
   }
}
