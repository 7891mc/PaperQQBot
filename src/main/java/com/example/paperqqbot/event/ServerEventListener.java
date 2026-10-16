package com.example.paperqqbot.event;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.PluginConfig;
import com.example.paperqqbot.core.ChatGroup;
import com.example.paperqqbot.placeholder.PlaceholderReplacer;
import com.example.paperqqbot.serverinfo.ServerInfoProvider;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.ServerLoadEvent.LoadType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ServerEventListener implements Listener {
   private final PaperQQBotPlugin plugin;
   private volatile long lastTpsWarningEpoch = 0L;
   private volatile boolean startPushed = false;

   public ServerEventListener(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onServerLoad(@NotNull ServerLoadEvent event) {
      if (event.getType() == LoadType.STARTUP) {
         long delayTicks = this.plugin.config().getMisc().startPushDelayTicks();
         Bukkit.getScheduler().runTaskLater(this.plugin, this::pushStart, Math.max(1L, delayTicks));
      }
   }

   public void pushStart() {
      if (!this.startPushed) {
         this.startPushed = true;
         this.plugin.serverInfo().markStart();
         this.dispatchEvent("start", null);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
      Player p = event.getPlayer();
      PlaceholderReplacer.EventContext ctx = PlaceholderReplacer.forPlayer(p.getName(), p.getDisplayName());
      this.dispatchEvent("join", ctx);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
      Player p = event.getPlayer();
      PlaceholderReplacer.EventContext ctx = PlaceholderReplacer.forPlayer(p.getName(), p.getDisplayName());
      this.dispatchEvent("quit", ctx);
      PluginConfig cfg = this.plugin.config();
      if (cfg.getTriggers().emptyOnZeroPlayers() && cfg.getTriggers().emptyOnlyOnTransition()) {
         Bukkit.getScheduler().runTaskLater(this.plugin, this::maybePushEmpty, 1L);
      } else if (cfg.getTriggers().emptyOnZeroPlayers()) {
         this.maybePushEmpty();
      }
   }

   private void maybePushEmpty() {
      PluginConfig cfg = this.plugin.config();
      if (cfg.getTriggers().emptyOnZeroPlayers()) {
         ServerInfoProvider info = this.plugin.serverInfo();
         if (info.onlinePlayers() != 0) {
            info.setTransitionedEmpty(false);
         } else if (!cfg.getTriggers().emptyOnlyOnTransition() || !info.isTransitionedEmpty()) {
            info.setTransitionedEmpty(true);
            this.dispatchEvent("empty", null);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(@NotNull AsyncPlayerChatEvent event) {
      Player p = event.getPlayer();
      PlaceholderReplacer.EventContext ctx = PlaceholderReplacer.forChat(p.getName(), p.getDisplayName(), event.getMessage());
      this.dispatchEvent("chat", ctx);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(@NotNull PlayerDeathEvent event) {
      Player p = event.getEntity();
      String death = event.getDeathMessage();
      PlaceholderReplacer.EventContext ctx = PlaceholderReplacer.forDeath(p.getName(), p.getDisplayName(), death);
      this.dispatchEvent("death", ctx);
   }

   public void checkTpsWarning() {
      PluginConfig cfg = this.plugin.config();
      if (cfg.getTriggers().tpsWarningEnabled()) {
         double[] tps = this.plugin.serverInfo().tps();
         double tps1m = tps.length > 0 ? tps[0] : 20.0;
         if (!(tps1m >= cfg.getTriggers().tpsWarningThreshold())) {
            long now = System.currentTimeMillis();
            if (now - this.lastTpsWarningEpoch >= cfg.getTriggers().tpsWarningCooldownSeconds() * 1000L) {
               this.lastTpsWarningEpoch = now;
               this.dispatchEvent("tps-warning", null);
            }
         }
      }
   }

   public void pushStopSync(long waitMillis) {
      PluginConfig cfg = this.plugin.config();
      if (cfg.isEventEnabled("stop")) {
         List<ChatGroup> targets = this.plugin.chatGroups().stream().filter(g -> g.acceptEvent("stop")).toList();
         if (!targets.isEmpty()) {
            String template = cfg.getTemplates().get("stop");
            String message = this.plugin.placeholder().replace(template, null);
            if (message != null && !message.isBlank()) {
               for (ChatGroup g2 : targets) {
                  try {
                     this.plugin.messageSender().sendGroupMessage(g2.key(), message).get(waitMillis, TimeUnit.MILLISECONDS);
                  } catch (TimeoutException var10) {
                     this.plugin.pluginLogger().warning("[PaperQQBot] 关服推送超时，群=" + g2.key());
                  } catch (Exception var11) {
                     this.plugin.pluginLogger().warning("[PaperQQBot] 关服推送失败 群=" + g2.key() + " " + var11.getMessage());
                  }
               }
            }
         }
      }
   }

   public void dispatchEvent(@NotNull String eventName, @Nullable PlaceholderReplacer.EventContext ctx) {
      PluginConfig cfg = this.plugin.config();
      if (cfg.isEventEnabled(eventName)) {
         List<ChatGroup> targets = this.plugin.chatGroups().stream().filter(g -> g.acceptEvent(eventName)).toList();
         if (!targets.isEmpty()) {
            String template = cfg.getTemplates().get(eventName);
            if (template != null && !template.isBlank()) {
               String message = this.plugin.placeholder().replace(template, ctx);
               if (message != null && !message.isBlank()) {
                  for (ChatGroup g2 : targets) {
                     this.plugin.messageSender().sendGroupMessage(g2.key(), message);
                  }
               }
            }
         }
      }
   }
}
