package com.example.paperqqbot.bot;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.BotSettings;
import com.example.paperqqbot.config.GroupTarget;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class NapCatClient {
   private static final String SEND_GROUP_MSG_PATH = "/send_group_msg";
   private final PaperQQBotPlugin plugin;
   private final HttpClient httpClient;
   private final ExecutorService executor;
   private volatile BotSettings settings;

   public NapCatClient(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.settings = plugin.config().getBot();
      this.executor = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r, "PaperQQBot-Http-Worker");
         t.setDaemon(true);
         return t;
      });
      this.httpClient = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds((long)Math.max(1, this.settings.connectTimeoutSec())))
         .version(Version.HTTP_1_1)
         .build();
   }

   public void reloadSettings(BotSettings settings) {
      this.settings = settings;
   }

   public CompletableFuture<Boolean> sendGroupMessageAsync(long groupId, String message) {
      if (!this.settings.hasUrl()) {
         this.plugin.pluginLogger().warning("[NapCat] http-url 未配置，跳过发送。");
         return CompletableFuture.completedFuture(false);
      } else {
         return CompletableFuture.supplyAsync(() -> this.sendWithRetry(groupId, message), this.executor);
      }
   }

   public void sendToGroups(List<GroupTarget> targets, String message) {
      for (GroupTarget g : targets) {
         this.sendGroupMessageAsync(g.groupId(), message);
      }
   }

   private boolean sendWithRetry(long groupId, String message) {
      int attempts = Math.max(0, this.settings.retry()) + 1;
      AtomicInteger attempt = new AtomicInteger(0);

      for (int i = 0; i < attempts; i++) {
         attempt.set(i + 1);

         try {
            boolean ok = this.doSend(groupId, message);
            if (ok) {
               return true;
            }
         } catch (Exception var9) {
            if (this.settings.debug()) {
               this.plugin.pluginLogger().log(Level.WARNING, "[NapCat] 发送至群 " + groupId + " 第 " + attempt.get() + " 次失败：" + var9.getMessage(), (Throwable)var9);
            }

            this.plugin.pluginLogger().warning("[NapCat] 发送至群 " + groupId + " 第 " + attempt.get() + " 次失败：" + var9.getMessage());
         }

         if (i < attempts - 1) {
            try {
               Thread.sleep(this.settings.retryIntervalMs());
            } catch (InterruptedException var8) {
               Thread.currentThread().interrupt();
               return false;
            }
         }
      }

      return false;
   }

   private boolean doSend(long groupId, String message) throws Exception {
      String body = buildSendGroupMsgBody(groupId, message);
      Builder builder = HttpRequest.newBuilder()
         .uri(URI.create(trimTrailingSlash(this.settings.httpUrl()) + "/send_group_msg"))
         .timeout(Duration.ofSeconds((long)Math.max(1, this.settings.readTimeoutSec())))
         .header("Content-Type", "application/json; charset=UTF-8")
         .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      String auth = this.settings.authHeader();
      if (auth != null) {
         builder.header("Authorization", auth);
      }

      HttpResponse<String> resp = this.httpClient.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
      int code = resp.statusCode();
      String respBody = resp.body();
      if (code < 200 || code >= 300) {
         this.plugin.pluginLogger().warning("[NapCat] send_group_msg HTTP " + code + " 群=" + groupId + " body=" + truncate(respBody));
         return false;
      } else if (respBody != null && respBody.matches("(?s).*\"retcode\"\\s*:\\s*0\\b.*")) {
         return true;
      } else {
         this.plugin.pluginLogger().warning("[NapCat] send_group_msg 返回异常 群=" + groupId + " body=" + truncate(respBody));
         return false;
      }
   }

   private static String buildSendGroupMsgBody(long groupId, String message) {
      return "{\"group_id\":" + groupId + ",\"message\":\"" + escapeJson(message) + "\",\"auto_escape\":true}";
   }

   private static String escapeJson(String s) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(s.length() + 16);

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
               case '\b':
                  sb.append("\\b");
                  break;
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append("\\n");
                  break;
               case '\f':
                  sb.append("\\f");
                  break;
               case '\r':
                  sb.append("\\r");
                  break;
               case '"':
                  sb.append("\\\"");
                  break;
               case '\\':
                  sb.append("\\\\");
                  break;
               default:
                  if (c < ' ') {
                     sb.append(String.format("\\u%04x", c));
                  } else {
                     sb.append(c);
                  }
            }
         }

         return sb.toString();
      }
   }

   private static String trimTrailingSlash(String url) {
      if (url == null) {
         return "";
      } else {
         return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      }
   }

   private static String truncate(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() > 400 ? s.substring(0, 400) + "..." : s;
      }
   }

   public void shutdown() {
      try {
         this.executor.shutdown();
         if (!this.executor.awaitTermination(3L, TimeUnit.SECONDS)) {
            this.executor.shutdownNow();
         }
      } catch (InterruptedException var2) {
         Thread.currentThread().interrupt();
         this.executor.shutdownNow();
      }
   }
}
