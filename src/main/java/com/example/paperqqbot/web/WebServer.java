package com.example.paperqqbot.web;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.WebSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class WebServer {
   private final PaperQQBotPlugin plugin;
   private final WebApiHandler apiHandler;
   private final ExecutorService executor;
   private final AtomicReference<HttpServer> serverRef = new AtomicReference<>();
   private volatile byte[] htmlCache;
   private final WebSettings startSettings;

   public WebServer(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.apiHandler = new WebApiHandler(plugin);
      this.startSettings = plugin.config().getWeb();
      this.executor = Executors.newFixedThreadPool(4, r -> {
         Thread t = new Thread(r, "PaperQQBot-Web-Worker");
         t.setDaemon(true);
         return t;
      });
   }

   public boolean needsRestart(WebSettings newSettings) {
      if (newSettings == null) {
         return false;
      } else {
         return this.startSettings == null
            ? true
            : this.startSettings.enabled() != newSettings.enabled()
               || !this.startSettings.host().equals(newSettings.host())
               || this.startSettings.port() != newSettings.port()
               || !this.startSettings.accessToken().equals(newSettings.accessToken());
      }
   }

   public void start() {
      WebSettings cfg = this.plugin.config().getWeb();
      if (!cfg.enabled()) {
         this.plugin.pluginLogger().info("[WebServer] 未启用（web.enabled=false）");
      } else {
         try {
            HttpServer server = HttpServer.create(new InetSocketAddress(cfg.host(), cfg.port()), 0);
            server.createContext("/", new WebServer.StaticHandler(this));
            server.createContext("/api/", this.apiHandler);
            server.setExecutor(this.executor);
            server.start();
            this.serverRef.set(server);
            this.plugin.pluginLogger().info("[WebServer] 已启动：http://" + cfg.host() + ":" + cfg.port() + (cfg.accessToken().isBlank() ? "（无鉴权）" : "（已开启鉴权）"));
         } catch (IOException var3) {
            this.plugin.pluginLogger().log(Level.SEVERE, "[WebServer] 启动失败：" + var3.getMessage(), (Throwable)var3);
         }
      }
   }

   public void stop() {
      HttpServer server = this.serverRef.getAndSet(null);
      if (server != null) {
         server.stop(1);
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
      return this.serverRef.get() != null;
   }

   static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
      byte[] data = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(status, (long)data.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(data);
      }
   }

   private final class StaticHandler implements HttpHandler {
      final WebServer this$0;

      private StaticHandler(WebServer webServer) {
         Objects.requireNonNull(WebServer.this);
         super();
         Objects.requireNonNull(webServer);
         this.this$0 = webServer;
      }

      @Override
      public void handle(HttpExchange exchange) throws IOException {
         String path = exchange.getRequestURI().getPath();
         if (!"/".equals(path) && !"/index.html".equals(path)) {
            WebServer.sendText(exchange, 404, "Not Found", "text/plain; charset=UTF-8");
         } else {
            byte[] html = this.loadHtml();
            if (html == null) {
               WebServer.sendText(exchange, 500, "WebUI resource missing", "text/plain; charset=UTF-8");
            } else {
               exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
               exchange.getResponseHeaders().set("Cache-Control", "no-cache");
               exchange.sendResponseHeaders(200, (long)html.length);

               try (OutputStream os = exchange.getResponseBody()) {
                  os.write(html);
               }
            }
         }
      }

      private byte[] loadHtml() {
         byte[] cached = this.this$0.htmlCache;
         if (cached != null) {
            return cached;
         } else {
            try {
               byte[] var4;
               try (InputStream is = this.this$0.plugin.getResource("web/index.html")) {
                  if (is == null) {
                     return null;
                  }

                  this.this$0.htmlCache = cached = is.readAllBytes();
                  var4 = cached;
               }

               return var4;
            } catch (IOException var7) {
               return null;
            }
         }
      }
   }
}
