package com.example.paperqqbot.account;

import com.example.paperqqbot.PaperQQBotPlugin;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;

public final class ServerReporter {
   private final PaperQQBotPlugin plugin;
   private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "PaperQQBot-Report");
      t.setDaemon(true);
      return t;
   });

   public ServerReporter(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   public void reportStart() {
      try {
         this.io.submit(() -> {
            try {
               Thread.sleep(3000L);
            } catch (InterruptedException var2x) {
               return;
            }

            this.send("start");
         });
      } catch (Throwable var2) {
      }
   }

   public void reportStop() {
      try {
         Future<?> fut = this.io.submit(() -> this.send("shutdown"));
         fut.get(6L, TimeUnit.SECONDS);
      } catch (Throwable var2) {
      }
   }

   public void shutdown() {
      try {
         this.io.shutdown();
         if (!this.io.awaitTermination(2L, TimeUnit.SECONDS)) {
            this.io.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.io.shutdownNow();
      }
   }

   private void send(String event) {
      try {
         LinkedHashMap<String, Object> f = new LinkedHashMap<>();
         f.put("event", event);
         f.put("port", Integer.valueOf(Bukkit.getPort()));
         f.put("name", this.plugin.config().getServer().name());
         f.put("version", this.plugin.serverInfo().minecraftVersion());
         f.put("motd", this.plugin.serverInfo().motd());
         f.put("players", Integer.valueOf(this.plugin.serverInfo().onlinePlayers()));
         f.put("max", Integer.valueOf(this.plugin.serverInfo().maxPlayers()));
         AccountClient.Result r = this.plugin.accountClient().report(f);
         if (!r.ok) {
            this.plugin.pluginLogger().warning("[Report] 服务器信息上报失败：" + r.error);
         }
      } catch (Throwable var4) {
         this.plugin.pluginLogger().warning("[Report] 上报异常：" + var4.getMessage());
      }
   }
}
