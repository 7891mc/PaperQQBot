package com.example.paperqqbot.official;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.core.InboundGroupMessage;
import com.example.paperqqbot.util.SimpleJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

final class OfficialBotWs {
   private static final String GATEWAY_URL = "https://api.sgroup.qq.com/gateway";
   private final PaperQQBotPlugin plugin;
   private final HttpClient httpClient;
   private final ScheduledExecutorService scheduler;
   private final OfficialBotService service;
   private final Object bufferLock = new Object();
   private final StringBuilder buffer = new StringBuilder();
   private volatile boolean running = false;
   private volatile boolean connected = false;
   private volatile String sessionId = null;
   private volatile String botUserId = null;
   private final AtomicLong lastSeq = new AtomicLong(0L);
   private volatile long heartbeatIntervalMs = 30000L;
   private volatile ScheduledFuture<?> heartbeatTask;
   private volatile long lastHeartbeatAck = 0L;
   private volatile long lastHeartbeatSent = 0L;
   private volatile int reconnectAttempts = 0;

   OfficialBotWs(PaperQQBotPlugin plugin, HttpClient httpClient, ScheduledExecutorService scheduler, OfficialBotService service) {
      this.plugin = plugin;
      this.httpClient = httpClient;
      this.scheduler = scheduler;
      this.service = service;
   }

   void start() {
      this.running = true;
      this.scheduler.submit(this::runLoop);
   }

   void stop() {
      this.running = false;
      this.connected = false;
      this.cancelHeartbeat();
   }

   boolean isConnected() {
      return this.connected && this.sessionId != null;
   }

   private void runLoop() {
      while (this.running) {
         CountDownLatch closed = new CountDownLatch(1);

         try {
            this.connect(closed);
            closed.await();
         } catch (Exception var5) {
            if (this.running) {
               this.plugin.pluginLogger().warning("[OfficialBot] 连接异常：" + var5.getMessage());
            }
         }

         this.connected = false;
         this.cancelHeartbeat();
         if (!this.running) {
            break;
         }

         long delay = backoffDelay(++this.reconnectAttempts);
         this.plugin.pluginLogger().info("[OfficialBot] " + delay / 1000L + " 秒后重连（" + (this.sessionId != null ? "尝试恢复会话" : "重新鉴权") + "）");

         try {
            Thread.sleep(delay);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            break;
         }
      }
   }

   private void connect(CountDownLatch closed) throws Exception {
      String token = this.service.accessToken();
      String gateway = this.fetchGateway(token);
      this.buffer.setLength(0);
      this.httpClient
         .newWebSocketBuilder()
         .connectTimeout(Duration.ofSeconds(15L))
         .buildAsync(URI.create(gateway), new OfficialBotWs.GatewayListener(this, closed, token))
         .get(20L, TimeUnit.SECONDS);
   }

   private String fetchGateway(String token) throws Exception {
      HttpRequest req = HttpRequest.newBuilder()
         .uri(URI.create("https://api.sgroup.qq.com/gateway"))
         .timeout(Duration.ofSeconds(10L))
         .header("Authorization", "QQBot " + token)
         .build();
      HttpResponse<String> resp = this.httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
         Map<String, Object> json = SimpleJson.parseObject(resp.body());
         String url = SimpleJson.getString(json, "url");
         if (url.isBlank()) {
            throw new IllegalStateException("网关地址为空：" + truncate(resp.body()));
         } else {
            return url;
         }
      } else {
         throw new IllegalStateException("获取网关地址失败 HTTP " + resp.statusCode());
      }
   }

   private void sendIdentify(WebSocket socket, String token) {
      String payload = "{\"op\":2,\"d\":{\"token\":\"QQBot "
         + SimpleJson.escape(token)
         + "\",\"intents\":33554432,\"shard\":[0,1],\"properties\":{\"$os\":\"linux\",\"$browser\":\"PaperQQBot\",\"$device\":\"PaperQQBot\"}}}";
      socket.sendText(payload, true);
      this.plugin.pluginLogger().info("[OfficialBot] 已发送鉴权请求，等待 Ready…");
   }

   private void sendResume(WebSocket socket, String token) {
      String payload = "{\"op\":6,\"d\":{\"token\":\"QQBot "
         + SimpleJson.escape(token)
         + "\",\"session_id\":\""
         + SimpleJson.escape(this.sessionId)
         + "\",\"seq\":"
         + this.lastSeq.get()
         + "}}";
      socket.sendText(payload, true);
      this.plugin.pluginLogger().info("[OfficialBot] 已发送会话恢复请求…");
   }

   private void startHeartbeat(WebSocket socket) {
      this.cancelHeartbeat();
      this.lastHeartbeatAck = System.currentTimeMillis();
      long interval = Math.max(5000L, this.heartbeatIntervalMs);
      long initial = (long)((double)interval * (0.3 + Math.random() * 0.7));
      this.heartbeatTask = this.scheduler.scheduleAtFixedRate(() -> {
         try {
            long now = System.currentTimeMillis();
            if (now - this.lastHeartbeatAck > interval * 2L + 1000L) {
               this.plugin.pluginLogger().warning("[OfficialBot] 心跳 ACK 超时，主动断开重连");
               this.closeQuietly(socket);
               return;
            }

            this.lastHeartbeatSent = now;
            String payload = "{\"op\":1,\"d\":" + (this.lastSeq.get() > 0L ? this.lastSeq.get() : "null") + "}";
            socket.sendText(payload, true);
         } catch (Throwable var7) {
            this.closeQuietly(socket);
         }
      }, initial, interval, TimeUnit.MILLISECONDS);
   }

   private void cancelHeartbeat() {
      ScheduledFuture<?> t = this.heartbeatTask;
      if (t != null) {
         t.cancel(false);
         this.heartbeatTask = null;
      }
   }

   private void closeQuietly(WebSocket socket) {
      try {
         socket.sendClose(1000, "reconnect");
      } catch (Throwable var3) {
      }
   }

   private void handlePayload(String json, WebSocket socket, String token) {
      try {
         Map<String, Object> payload = SimpleJson.parseObject(json);
         long op = SimpleJson.getLong(payload, "op", -1L);
         long s = SimpleJson.getLong(payload, "s", -1L);
         if (s > 0L) {
            this.lastSeq.accumulateAndGet(s, Math::max);
         }

         switch ((int)op) {
            case 0:
               this.handleDispatch(payload);
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 8:
            default:
               break;
            case 7:
               this.plugin.pluginLogger().info("[OfficialBot] 平台要求重连");
               this.closeQuietly(socket);
               break;
            case 9:
               this.plugin.pluginLogger().warning("[OfficialBot] 会话失效，将重新鉴权");
               this.sessionId = null;
               this.lastSeq.set(0L);
               this.closeQuietly(socket);
               break;
            case 10:
               Map<String, Object> d = SimpleJson.asObject(payload.get("d"));
               this.heartbeatIntervalMs = SimpleJson.getLong(d, "heartbeat_interval", 45000L);
               this.startHeartbeat(socket);
               if (this.sessionId != null) {
                  this.sendResume(socket, token);
               } else {
                  this.sendIdentify(socket, token);
               }
               break;
            case 11:
               this.lastHeartbeatAck = System.currentTimeMillis();
         }
      } catch (Throwable var10) {
         this.plugin.pluginLogger().log(Level.WARNING, "[OfficialBot] 处理网关消息失败：" + var10.getMessage(), var10);
      }
   }

   private void handleDispatch(Map<String, Object> payload) {
      String type = SimpleJson.getString(payload, "t");
      Map<String, Object> d = SimpleJson.asObject(payload.get("d"));
      switch (type) {
         case "READY":
            this.sessionId = SimpleJson.getString(d, "session_id");
            this.connected = true;
            this.reconnectAttempts = 0;
            Map<String, Object> user = SimpleJson.asObject(d.get("user"));
            this.botUserId = SimpleJson.getString(user, "id");
            this.plugin.pluginLogger().info("[OfficialBot] 鉴权成功，机器人 " + SimpleJson.getString(user, "username") + " 已上线，等待消息。");
            this.service.onReady();
            break;
         case "RESUMED":
            this.connected = true;
            this.reconnectAttempts = 0;
            this.plugin.pluginLogger().info("[OfficialBot] 会话已恢复。");
            break;
         case "GROUP_AT_MESSAGE_CREATE":
            this.routeGroupMessage(d, true);
            break;
         case "GROUP_MESSAGE_CREATE":
            this.routeGroupMessage(d, mentionsBot(d, this.botUserId));
            break;
         case "C2C_MESSAGE_CREATE":
            this.handlePrivate(d);
            break;
         case "GROUP_ADD_ROBOT":
            String groupOpenid = SimpleJson.getString(d, "group_openid");
            this.plugin.officialGroups().learn(groupOpenid, "");
      }
   }

   private static boolean mentionsBot(Map<String, Object> d, String selfId) {
      for (Object o : SimpleJson.asList(d.get("mentions"))) {
         Map<String, Object> u = SimpleJson.asObject(o);
         String id = SimpleJson.getString(u, "id");
         if (selfId != null && !selfId.isBlank() ? id.equalsIgnoreCase(selfId) : Boolean.TRUE.equals(u.get("bot"))) {
            return true;
         }
      }

      return false;
   }

   private void routeGroupMessage(Map<String, Object> d, boolean atBot) {
      String id = SimpleJson.getString(d, "id");
      String groupOpenid = SimpleJson.getString(d, "group_openid");
      Map<String, Object> author = SimpleJson.asObject(d.get("author"));
      String memberOpenid = SimpleJson.getString(author, "member_openid");
      Object username = SimpleJson.getString(author, "username");
      String memberRole = SimpleJson.getString(author, "member_role");
      String content = SimpleJson.getString(d, "content");
      ArrayList<String> attachments = new ArrayList<>();

      for (Object o : SimpleJson.asList(d.get("attachments"))) {
         Map<String, Object> a = SimpleJson.asObject(o);
         String ct = SimpleJson.getString(a, "content_type");
         if ("voice".equalsIgnoreCase(ct)) {
            attachments.add("[语音]");
         } else if (ct.startsWith("image/")) {
            attachments.add("[图片]");
         } else if (ct.startsWith("video/")) {
            attachments.add("[视频]");
         } else {
            String filename = SimpleJson.getString(a, "filename");
            attachments.add("[文件" + (filename.isBlank() ? "" : ":" + filename) + "]");
         }
      }

      if (((String)username).isBlank()) {
         String tail = memberOpenid.length() > 6 ? memberOpenid.substring(memberOpenid.length() - 6) : memberOpenid;
         username = "QQ-" + tail;
      }

      InboundGroupMessage msg = new InboundGroupMessage(
         groupOpenid,
         0L,
         memberOpenid.isBlank() ? null : memberOpenid,
         (String)username,
         content.trim(),
         attachments,
         0L,
         id,
         atBot,
         atBot,
         "owner".equalsIgnoreCase(memberRole)
      );
      this.plugin.inMessageRouter().route(msg);
   }

   private void handlePrivate(Map<String, Object> d) {
      String id = SimpleJson.getString(d, "id");
      Map<String, Object> author = SimpleJson.asObject(d.get("author"));
      String userOpenid = SimpleJson.getString(author, "user_openid");
      this.service.replyPrivate(userOpenid, "绑定、签到等功能请在 QQ 群内 @我 使用~", id);
   }

   private static long backoffDelay(int attempt) {
      long[] table = new long[]{2000L, 2000L, 5000L, 10000L, 20000L, 30000L};
      return table[Math.min(attempt - 1, table.length - 1)];
   }

   private static String truncate(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() > 300 ? s.substring(0, 300) + "..." : s;
      }
   }

   private final class GatewayListener implements Listener {
      private final CountDownLatch closed;
      private final String token;
      private volatile WebSocket socket;
      final OfficialBotWs this$0;

      GatewayListener(OfficialBotWs officialBotWs, CountDownLatch closed, String token) {
         Objects.requireNonNull(OfficialBotWs.this);
         super();
         Objects.requireNonNull(officialBotWs);
         this.this$0 = officialBotWs;
         this.closed = closed;
         this.token = token;
      }

      @Override
      public void onOpen(WebSocket webSocket) {
         this.socket = webSocket;
         this.this$0.connected = false;
         webSocket.request(1L);
      }

      public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
         Object object = this.this$0.bufferLock;
         synchronized (object) {
            this.this$0.buffer.append(data);
         }

         if (last) {
            Object object2 = this.this$0.bufferLock;
            String msg;
            synchronized (object2) {
               msg = this.this$0.buffer.toString();
               this.this$0.buffer.setLength(0);
            }

            this.this$0.handlePayload(msg, webSocket, this.token);
         }

         webSocket.request(1L);
         return null;
      }

      public CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
         webSocket.request(1L);
         return null;
      }

      public CompletableFuture<?> onPing(WebSocket webSocket, ByteBuffer message) {
         webSocket.request(1L);
         return null;
      }

      public CompletableFuture<?> onPong(WebSocket webSocket, ByteBuffer message) {
         webSocket.request(1L);
         return null;
      }

      public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
         this.this$0.connected = false;
         this.this$0.cancelHeartbeat();
         webSocket.request(1L);
         this.closed.countDown();
         return null;
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
         this.this$0.connected = false;
         this.this$0.cancelHeartbeat();
         if (this.this$0.running && error != null) {
            this.this$0.plugin.pluginLogger().warning("[OfficialBot] 网关错误：" + error.getMessage());
         }

         this.closed.countDown();
      }
   }
}
