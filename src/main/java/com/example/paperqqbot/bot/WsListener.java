package com.example.paperqqbot.bot;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.WsSettings;
import com.example.paperqqbot.core.InboundGroupMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Builder;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class WsListener {
   private final PaperQQBotPlugin plugin;
   private final WsSettings settings;
   private final AtReplyHandler atReplyHandler;
   private final HttpClient httpClient;
   private final ExecutorService executor;
   private final Object bufferLock = new Object();
   private final StringBuilder buffer = new StringBuilder();
   private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
   private volatile boolean running = false;
   private volatile boolean connected = false;
   private volatile long lastReconnectAt = 0L;

   public WsListener(PaperQQBotPlugin plugin, AtReplyHandler atReplyHandler) {
      this.plugin = plugin;
      this.settings = plugin.config().getWs();
      this.atReplyHandler = atReplyHandler;
      this.executor = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "PaperQQBot-WsListener");
         t.setDaemon(true);
         return t;
      });
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).executor(this.executor).build();
   }

   public void start() {
      if (!this.settings.enabled()) {
         this.plugin.pluginLogger().info("[WsListener] 未启用（ws.enabled=false）");
      } else if (!this.settings.hasUrl()) {
         this.plugin.pluginLogger().warning("[WsListener] ws.url 未配置，跳过启动");
      } else {
         this.running = true;
         this.scheduleConnect(0L);
      }
   }

   public void stop() {
      this.running = false;
      this.connected = false;
      WebSocket ws = this.wsRef.getAndSet(null);
      if (ws != null) {
         try {
            ws.sendClose(1000, "plugin shutdown");
         } catch (Throwable var4) {
         }
      }

      try {
         this.executor.shutdown();
         if (!this.executor.awaitTermination(2L, TimeUnit.SECONDS)) {
            this.executor.shutdownNow();
         }
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         this.executor.shutdownNow();
      }
   }

   public boolean isRunning() {
      return this.running;
   }

   public boolean isConnected() {
      return this.connected;
   }

   private void scheduleConnect(long delayMs) {
      if (this.running) {
         long now = System.currentTimeMillis();
         if (now - this.lastReconnectAt >= 500L) {
            this.lastReconnectAt = now;
            this.executor.submit(() -> {
               if (this.running) {
                  if (delayMs > 0L) {
                     try {
                        Thread.sleep(delayMs);
                     } catch (InterruptedException var4) {
                        Thread.currentThread().interrupt();
                        return;
                     }
                  }

                  this.doConnect();
               }
            });
         }
      }
   }

   private void doConnect() {
      if (this.running) {
         try {
            Builder b = this.httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10L));
            String auth = this.settings.authHeader();
            if (auth != null) {
               b.header("Authorization", auth);
            }

            b.buildAsync(URI.create(this.settings.url()), new WsListener.Listener(this)).whenComplete((ws, ex) -> {
               if (ex != null) {
                  this.plugin.pluginLogger().warning("[WsListener] 连接失败：" + ex.getMessage());
                  this.scheduleConnect(this.settings.reconnectMs());
               }
            });
         } catch (Throwable var3) {
            this.plugin.pluginLogger().warning("[WsListener] 启动异常：" + var3.getMessage());
            this.scheduleConnect(this.settings.reconnectMs());
         }
      }
   }

   private void handleMessage(String json) {
      try {
         OneBotEventParser.GroupMessageEvent event = OneBotEventParser.parseGroupMessage(json);
         if (event == null) {
            return;
         }

         long selfId = this.atReplyHandler.resolveSelfId(this.plugin.config().getAtReply().selfId(), event.selfId());
         boolean atBot = selfId > 0L && event.atTargets().contains(selfId);
         Object name = event.senderName();
         if (name == null || ((String)name).isBlank()) {
            name = "QQ-" + event.userId();
         }

         InboundGroupMessage msg = new InboundGroupMessage(
            String.valueOf(event.groupId()),
            event.userId(),
            null,
            (String)name,
            event.text(),
            event.media(),
            selfId,
            event.messageId(),
            atBot,
            false,
            "owner".equalsIgnoreCase(event.senderRole())
         );
         this.plugin.inMessageRouter().route(msg);
      } catch (Throwable var8) {
         this.plugin.pluginLogger().log(Level.WARNING, "[WsListener] 处理事件失败：" + var8.getMessage(), var8);
      }
   }

   private final class Listener implements WebSocket.Listener {
      final WsListener this$0;

      private Listener(WsListener wsListener) {
         Objects.requireNonNull(WsListener.this);
         super();
         Objects.requireNonNull(wsListener);
         this.this$0 = wsListener;
      }

      @Override
      public void onOpen(WebSocket webSocket) {
         this.this$0.wsRef.set(webSocket);
         this.this$0.connected = true;
         this.this$0.plugin.pluginLogger().info("[WsListener] 已连接到 NapCat WS：" + this.this$0.settings.url());
         webSocket.request(1L);
      }

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
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

            this.this$0.handleMessage(msg);
         }

         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
         this.this$0.connected = false;
         this.this$0.wsRef.compareAndSet(webSocket, null);
         this.this$0.plugin.pluginLogger().info("[WsListener] 连接关闭 code=" + statusCode + " reason=" + reason);
         this.this$0.scheduleConnect(this.this$0.settings.reconnectMs());
         return null;
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
         this.this$0.connected = false;
         this.this$0.wsRef.compareAndSet(webSocket, null);
         this.this$0.plugin.pluginLogger().warning("[WsListener] 错误：" + error.getMessage());
         this.this$0.scheduleConnect(this.this$0.settings.reconnectMs());
      }
   }
}
