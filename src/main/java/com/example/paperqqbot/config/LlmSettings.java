package com.example.paperqqbot.config;

public record LlmSettings(
   boolean enabled, String apiUrl, String apiKey, String model, String systemPrompt, int timeoutSeconds, double temperature, int contextTurns
) {
   public static LlmSettings fallback() {
      return new LlmSettings(false, "https://api.openai.com/v1/chat/completions", "", "gpt-4o-mini", "你是一个 Minecraft 服务器助手，用简洁友好的中文回答玩家关于服务器的问题。", 30, 0.7, 5);
   }

   public boolean hasApiKey() {
      return this.apiKey != null && !this.apiKey.isBlank();
   }

   public boolean hasApiUrl() {
      return this.apiUrl != null && !this.apiUrl.isBlank();
   }
}
