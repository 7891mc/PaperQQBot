package com.example.paperqqbot.official;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.AtReplySettings;
import com.example.paperqqbot.config.BindingSettings;
import com.example.paperqqbot.config.SignInSettings;
import com.example.paperqqbot.util.SimpleJson;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

final class CommandPanelManager {
   private static final String API_BASE = "https://api.sgroup.qq.com";
   private static final String MARKER = "PaperQQBot-GroupPanel";
   private final PaperQQBotPlugin plugin;
   private final HttpClient httpClient;
   private final Supplier<String> tokenSupplier;
   private final ExecutorService taskExecutor;

   CommandPanelManager(PaperQQBotPlugin plugin, HttpClient httpClient, Supplier<String> tokenSupplier) {
      this.plugin = plugin;
      this.httpClient = httpClient;
      this.tokenSupplier = tokenSupplier;
      this.taskExecutor = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "PaperQQBot-PanelSync");
         t.setDaemon(true);
         return t;
      });
   }

   void syncAsync() {
      this.taskExecutor.submit(() -> {
         String result = this.syncGroupPanel();
         this.plugin.pluginLogger().info("[OfficialBot] " + result);
      });
   }

   void shutdown() {
      this.taskExecutor.shutdownNow();
   }

   private String syncGroupPanel() {
      try {
         String panelInner = "\"items\":[" + this.buildItemsJson() + "],\"remark\":\"PaperQQBot-GroupPanel\"";
         String existing = this.findPanelId();
         if (existing != null && !existing.isBlank()) {
            HttpResponse<String> resp = this.send(
               "PUT", "https://api.sgroup.qq.com/v2/panels/" + URLEncoder.encode(existing, StandardCharsets.UTF_8), "{\"panel\":{" + panelInner + "}}"
            );
            ensureSuccess(resp, "更新");
            return "指令面板已更新，群内 @ 机器人即可看到指令面板。";
         } else {
            String postBody = "{\"scope\":\"group\",\"target_type\":\"all\",\"panel\":{" + panelInner + "}}";
            HttpResponse<String> resp = this.send("POST", "https://api.sgroup.qq.com/v2/panels", postBody);
            ensureSuccess(resp, "创建");
            String panelId = SimpleJson.getString(SimpleJson.parseObject(resp.body()), "panel_id");
            return "指令面板已创建" + (panelId.isBlank() ? "" : "（" + panelId + "）") + "，群内 @ 机器人即可看到指令面板。";
         }
      } catch (Exception var6) {
         this.plugin.pluginLogger().log(Level.WARNING, "[OfficialBot] 同步指令面板失败：" + var6.getMessage(), (Throwable)var6);
         return "同步指令面板失败：" + var6.getMessage();
      }
   }

   private String findPanelId() throws Exception {
      HttpResponse<String> resp = this.send("GET", "https://api.sgroup.qq.com/v2/panels?scope=group&limit=50", null);
      ensureSuccess(resp, "查询面板列表");
      Map<String, Object> root = SimpleJson.parseObject(resp.body());

      for (Object recObj : SimpleJson.asList(root.get("records"))) {
         Map<String, Object> rec = SimpleJson.asObject(recObj);
         Map<String, Object> panel = SimpleJson.asObject(rec.get("panel"));
         if ("PaperQQBot-GroupPanel".equals(SimpleJson.getString(panel, "remark"))) {
            return SimpleJson.getString(rec, "panel_id");
         }
      }

      return null;
   }

   private String buildItemsJson() {
      ArrayList<String> items = new ArrayList<>();
      BindingSettings bind = this.plugin.config().getBinding();
      SignInSettings sign = this.plugin.config().getSignIn();
      AtReplySettings at = this.plugin.config().getAtReply();
      if (bind.enabled()) {
         items.add(commandItem(bind.normalizedRequestCommand(), "发送 /绑定 验证码 完成绑定"));
      }

      if (sign.enabled()) {
         items.add(commandItem(sign.normalizedCommand(), "每日签到领取金币"));
      }

      String query;
      if ((query = at.queryCommand()) == null || query.isBlank()) {
         query = "/查询";
      }

      items.add(commandItem(query.trim(), "查询服务器在线信息"));
      items.add(commandItem("/执行", "执行控制台指令"));
      return String.join(",", items);
   }

   private static String commandItem(String name, String desc) {
      return "{\"type\":\"command\",\"name\":\"" + SimpleJson.escape(name) + "\",\"desc\":\"" + SimpleJson.escape(desc) + "\"}";
   }

   private HttpResponse<String> send(String method, String url, String body) throws Exception {
      String token;
      try {
         token = this.tokenSupplier.get();
      } catch (RuntimeException var6) {
         throw new IllegalStateException(var6.getMessage(), var6);
      }

      if (token != null && !token.isBlank()) {
         Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(12L))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Authorization", "QQBot " + token);
         if (body != null) {
            builder.method(method, BodyPublishers.ofString(body, StandardCharsets.UTF_8));
         } else {
            builder.method(method, BodyPublishers.noBody());
         }

         return this.httpClient.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
      } else {
         throw new IllegalStateException("无法获取 access_token，请检查机器人 ID 和密钥");
      }
   }

   private static void ensureSuccess(HttpResponse<String> resp, String action) {
      int code = resp.statusCode();
      if (code < 200 || code >= 300) {
         String b = resp.body();
         throw new IllegalStateException(action + "失败 HTTP " + code + (b == null ? "" : "：" + (b.length() > 300 ? b.substring(0, 300) + "..." : b)));
      }
   }
}
