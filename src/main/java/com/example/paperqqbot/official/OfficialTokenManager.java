package com.example.paperqqbot.official;

import com.example.paperqqbot.config.OfficialBotSettings;
import com.example.paperqqbot.util.SimpleJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;

final class OfficialTokenManager {
   private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
   private final HttpClient httpClient;
   private final OfficialBotSettings settings;
   private final Level loggerLevel;
   private volatile String cachedToken = "";
   private volatile long expireAt = 0L;

   OfficialTokenManager(HttpClient httpClient, OfficialBotSettings settings) {
      this.httpClient = httpClient;
      this.settings = settings;
      this.loggerLevel = Level.WARNING;
   }

   String getToken() {
      long now = System.currentTimeMillis();
      if (!this.cachedToken.isBlank() && this.expireAt - now > 60000L) {
         return this.cachedToken;
      } else {
         synchronized (this) {
            if (!this.cachedToken.isBlank() && this.expireAt - System.currentTimeMillis() > 60000L) {
               return this.cachedToken;
            } else {
               this.refresh();
               return this.cachedToken;
            }
         }
      }
   }

   private void refresh() {
      try {
         String body = "{\"appId\":\""
            + SimpleJson.escape(this.settings.appId())
            + "\",\"clientSecret\":\""
            + SimpleJson.escape(this.settings.secret())
            + "\"}";
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://bots.qq.com/app/getAppAccessToken"))
            .timeout(Duration.ofSeconds(15L))
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
         HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            Map<String, Object> json = SimpleJson.parseObject(resp.body());
            String token = SimpleJson.getString(json, "access_token");
            if (token.isBlank()) {
               throw new IllegalStateException("响应缺少 access_token：" + truncate(resp.body()));
            } else {
               long expiresIn = SimpleJson.getLong(json, "expires_in", 7200L);
               this.cachedToken = token;
               this.expireAt = System.currentTimeMillis() + Math.max(60L, expiresIn - 300L) * 1000L;
            }
         } else {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + truncate(resp.body()));
         }
      } catch (Exception var8) {
         this.cachedToken = "";
         this.expireAt = 0L;
         throw new OfficialBotException("获取 access_token 失败：" + var8.getMessage(), var8);
      }
   }

   void invalidate() {
      synchronized (this) {
         this.cachedToken = "";
         this.expireAt = 0L;
      }
   }

   private static String truncate(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() > 300 ? s.substring(0, 300) + "..." : s;
      }
   }

   Level loggerLevel() {
      return this.loggerLevel;
   }
}
