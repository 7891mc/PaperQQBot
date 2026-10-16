package com.example.paperqqbot.official;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.core.MessageSender;
import com.example.paperqqbot.util.SimpleJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class OfficialBotService implements MessageSender {
   public static final long INTENT_GROUP_AND_C2C = 33554432L;
   private static final String API_BASE = "https://api.sgroup.qq.com";
   private final PaperQQBotPlugin plugin;
   private final ExecutorService ioExecutor;
   private final ScheduledExecutorService scheduler;
   private final HttpClient httpClient;
   private final OfficialTokenManager tokenManager;
   private final CommandPanelManager panelManager;
   private final AtomicInteger msgSeq = new AtomicInteger(0);
   private volatile OfficialBotWs ws;

   public OfficialBotService(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.ioExecutor = Executors.newFixedThreadPool(3, r -> {
         Thread t = new Thread(r, "PaperQQBot-Official-IO");
         t.setDaemon(true);
         return t;
      });
      this.scheduler = Executors.newScheduledThreadPool(2, r -> {
         Thread t = new Thread(r, "PaperQQBot-Official-Scheduler");
         t.setDaemon(true);
         return t;
      });
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).executor(this.ioExecutor).version(Version.HTTP_1_1).build();
      this.tokenManager = new OfficialTokenManager(this.httpClient, plugin.config().getOfficialBot());
      this.panelManager = new CommandPanelManager(plugin, this.httpClient, this::accessToken);
   }

   public void start() {
      if (!this.plugin.config().getOfficialBot().ready()) {
         this.plugin.pluginLogger().warning("[OfficialBot] 未填写机器人 AppID 或密钥，官方机器人未启动。");
      } else {
         this.ws = new OfficialBotWs(this.plugin, this.httpClient, this.scheduler, this);
         this.ws.start();
      }
   }

   public void shutdown() {
      if (this.ws != null) {
         try {
            this.ws.stop();
         } catch (Throwable var2) {
         }

         this.ws = null;
      }

      shutdown(this.scheduler);
      shutdown(this.ioExecutor);
      this.panelManager.shutdown();
   }

   public boolean isConnected() {
      OfficialBotWs w = this.ws;
      return w != null && w.isConnected();
   }

   public boolean isRunning() {
      return this.ws != null;
   }

   String accessToken() {
      return this.tokenManager.getToken();
   }

   void invalidateToken() {
      this.tokenManager.invalidate();
   }

   void onReady() {
      this.panelManager.syncAsync();
   }

   @Override
   public CompletableFuture<Boolean> sendGroupMessage(String groupOpenid, String text) {
      return this.postGroupMessage(groupOpenid, "{\"content\":\"" + SimpleJson.escape(text) + "\",\"msg_type\":0}");
   }

   @Override
   public CompletableFuture<Boolean> replyToMessage(String groupOpenid, String text, String passiveMsgId) {
      int seq = this.nextSeq();
      String body = "{\"content\":\""
         + SimpleJson.escape(text)
         + "\",\"msg_type\":0,\"msg_id\":\""
         + SimpleJson.escape(passiveMsgId)
         + "\",\"msg_seq\":"
         + seq
         + "}";
      return this.postGroupMessage(groupOpenid, body);
   }

   public CompletableFuture<Boolean> replyPrivate(String userOpenid, String text, String passiveMsgId) {
      int seq = this.nextSeq();
      String body = "{\"content\":\""
         + SimpleJson.escape(text)
         + "\",\"msg_type\":0,\"msg_id\":\""
         + SimpleJson.escape(passiveMsgId)
         + "\",\"msg_seq\":"
         + seq
         + "}";
      return this.post("https://api.sgroup.qq.com/v2/users/" + userOpenid + "/messages", body);
   }

   private CompletableFuture<Boolean> postGroupMessage(String groupOpenid, String body) {
      return this.post("https://api.sgroup.qq.com/v2/groups/" + groupOpenid + "/messages", body);
   }

   private CompletableFuture<Boolean> post(String url, String body) {
      return CompletableFuture.supplyAsync(
         () -> {
            try {
               String token;
               try {
                  token = this.tokenManager.getToken();
               } catch (OfficialBotException var8) {
                  this.plugin.pluginLogger().warning("[OfficialBot] " + var8.getMessage());
                  return false;
               }

               HttpRequest req = HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .timeout(Duration.ofSeconds(10L))
                  .header("Content-Type", "application/json; charset=UTF-8")
                  .header("Authorization", "QQBot " + token)
                  .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                  .build();
               HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
               int code = resp.statusCode();
               if (code == 401 || code == 403) {
                  this.tokenManager.invalidate();
               }

               if (code >= 200 && code < 300) {
                  String rb = resp.body();
                  if (rb != null && rb.contains("\"id\"")) {
                     return true;
                  } else {
                     this.plugin.pluginLogger().warning("[OfficialBot] 发送返回异常：" + truncate(rb));
                     return false;
                  }
               } else {
                  this.plugin.pluginLogger().warning("[OfficialBot] 发送失败 HTTP " + code + "：" + truncate(resp.body()));
                  return false;
               }
            } catch (Exception var9) {
               this.plugin.pluginLogger().log(Level.WARNING, "[OfficialBot] 发送异常：" + var9.getMessage(), (Throwable)var9);
               return false;
            }
         },
         this.ioExecutor
      );
   }

   private int nextSeq() {
      for (int i = 0; i < 100; i++) {
         int v = this.msgSeq.updateAndGet(n -> n >= Integer.MAX_VALUE ? 1 : n + 1);
         if (v > 0) {
            return v;
         }
      }

      return 1;
   }

   private static void shutdown(ExecutorService es) {
      try {
         es.shutdown();
         if (!es.awaitTermination(3L, TimeUnit.SECONDS)) {
            es.shutdownNow();
         }
      } catch (InterruptedException var2) {
         Thread.currentThread().interrupt();
         es.shutdownNow();
      }
   }

   private static String truncate(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() > 400 ? s.substring(0, 400) + "..." : s;
      }
   }
}
