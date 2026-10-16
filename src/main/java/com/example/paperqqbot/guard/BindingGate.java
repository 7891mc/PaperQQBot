package com.example.paperqqbot.guard;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.BindingSettings;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class BindingGate implements Listener {
   private final PaperQQBotPlugin plugin;
   private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<UUID, BukkitTask> kickTasks = new ConcurrentHashMap<>();

   public BindingGate(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      BindingSettings bind = this.plugin.config().getBinding();
      if (bind.enabled() && bind.requireBind()) {
         if (this.plugin.bindingStore().findBindingByPlayer(uuid) == null) {
            if (this.plugin.bindingStore().isBlacklisted(null, uuid, player.getName())) {
               Bukkit.getScheduler().runTask(this.plugin, () -> player.kickPlayer("你已被加入黑名单，无法进入服务器，请联系管理员。"));
            } else {
               this.frozen.add(uuid);
               String message = this.plugin.bindingStore().autoIssueOnJoin(uuid, player.getName(), bind.allowRebind(), bind.codeExpireMinutes());
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  if (this.frozen.contains(uuid)) {
                     player.sendMessage(message);
                  }
               }, 2L);
               this.scheduleKick(uuid);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.frozen.remove(uuid);
      BukkitTask task = this.kickTasks.remove(uuid);
      if (task != null) {
         task.cancel();
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId()) && event.getTo() != null) {
         Location from = event.getFrom();
         Location to = event.getTo();
         if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onInteract(PlayerInteractEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.frozen.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      Entity entity = event.getDamager();
      Player damager;
      if (entity instanceof Player && this.frozen.contains((damager = (Player)entity).getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (this.frozen.contains(player.getUniqueId())) {
         String root = event.getMessage().trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
         if (root.startsWith("/")) {
            root = root.substring(1);
         }

         if (!root.equals("bindqq") && !root.equals("绑定qq") && !root.equals("paperqqbot") && !root.equals("pqb") && !root.equals("paperqq")) {
            player.sendMessage("完成 QQ 绑定前无法使用该指令。");
            event.setCancelled(true);
         }
      }
   }

   public void notifyBound(UUID uuid) {
      if (uuid != null) {
         Bukkit.getScheduler().runTask(this.plugin, () -> this.release(uuid));
      }
   }

   public void rescheduleKick(UUID uuid) {
      if (this.frozen.contains(uuid)) {
         this.scheduleKick(uuid);
      }
   }

   private void release(UUID uuid) {
      this.frozen.remove(uuid);
      BukkitTask task = this.kickTasks.remove(uuid);
      if (task != null) {
         task.cancel();
      }

      Player player;
      if ((player = Bukkit.getPlayer(uuid)) != null) {
         player.sendMessage("QQ 绑定成功！你现在可以自由活动了。");
      }
   }

   private void scheduleKick(UUID uuid) {
      Long expireAt = this.plugin.bindingStore().getCodeExpireAt(uuid);
      if (expireAt != null) {
         long delayTicks = Math.max(1L, (expireAt - System.currentTimeMillis() + 49L) / 50L);
         BukkitTask old = this.kickTasks.remove(uuid);
         if (old != null) {
            old.cancel();
         }

         BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.kickTasks.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
               if (this.plugin.bindingStore().findBindingByPlayer(uuid) != null) {
                  this.release(uuid);
               } else {
                  player.kickPlayer("你未在规定时间内绑定 QQ，已被移出服务器。\n请重新进入服务器，按提示完成绑定。");
               }
            }
         }, delayTicks);
         this.kickTasks.put(uuid, task);
      }
   }

   public void shutdown() {
      for (BukkitTask task : this.kickTasks.values()) {
         task.cancel();
      }

      this.kickTasks.clear();
      this.frozen.clear();
   }
}
