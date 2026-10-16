package com.example.paperqqbot.placeholder;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.ServerInfo;
import com.example.paperqqbot.serverinfo.ServerInfoProvider;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderReplacer {
   private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
   private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
   private final PaperQQBotPlugin plugin;

   public PlaceholderReplacer(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   @NotNull
   public String replace(@NotNull String template, @Nullable PlaceholderReplacer.EventContext context) {
      if (template != null && !template.isEmpty()) {
         ServerInfo s = this.plugin.config().getServer();
         ServerInfoProvider info = this.plugin.serverInfo();
         HashMap<String, String> vars = new HashMap<>();
         vars.put("server_name", safe(s.name()));
         vars.put("server_address", safe(s.address()));
         vars.put("server_contact", safe(s.contact()));
         vars.put("server_extra", safe(s.extra()));
         vars.put("server_website", safe(s.website()));
         vars.put("server_version", info.minecraftVersion());
         vars.put("server_motd", info.motd());
         vars.put("server_ip", info.serverIp());
         vars.put("server_port", String.valueOf(info.serverPort()));
         vars.put("online_players", String.valueOf(info.onlinePlayers()));
         vars.put("max_players", String.valueOf(info.maxPlayers()));
         vars.put("player_list", info.playerList());
         double[] tps = info.tps();
         vars.put("tps_1m", formatTps(tps.length > 0 ? tps[0] : -1.0));
         vars.put("tps_5m", formatTps(tps.length > 1 ? tps[1] : -1.0));
         vars.put("tps_15m", formatTps(tps.length > 2 ? tps[2] : -1.0));
         vars.put("memory_used_mb", String.valueOf(info.memoryUsedMb()));
         vars.put("memory_max_mb", String.valueOf(info.memoryMaxMb()));
         vars.put("uptime_seconds", String.valueOf(info.uptimeSeconds()));
         vars.put("uptime_formatted", info.uptimeFormatted());
         LocalDateTime now = LocalDateTime.now();
         vars.put("datetime", now.format(DATE_TIME_FMT));
         vars.put("date", now.format(DATE_FMT));
         vars.put("time", now.format(TIME_FMT));
         if (context != null) {
            vars.put("player_name", safe(context.playerName()));
            vars.put("player_display", safe(context.playerDisplay()));
            vars.put("death_message", safe(context.deathMessage()));
            vars.put("chat_message", safe(context.chatMessage()));
         } else {
            vars.put("player_name", "");
            vars.put("player_display", "");
            vars.put("death_message", "");
            vars.put("chat_message", "");
         }

         String result = template;

         for (Entry e : vars.entrySet()) {
            result = result.replace("%" + (String)e.getKey() + "%", (CharSequence)e.getValue());
         }

         return result;
      } else {
         return "";
      }
   }

   public static PlaceholderReplacer.EventContext forPlayer(String playerName, String playerDisplay) {
      return new PlaceholderReplacer.EventContext(playerName, playerDisplay, null, null);
   }

   public static PlaceholderReplacer.EventContext forDeath(String playerName, String playerDisplay, String deathMessage) {
      return new PlaceholderReplacer.EventContext(playerName, playerDisplay, deathMessage, null);
   }

   public static PlaceholderReplacer.EventContext forChat(String playerName, String playerDisplay, String chatMessage) {
      return new PlaceholderReplacer.EventContext(playerName, playerDisplay, null, chatMessage);
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }

   private static String formatTps(double t) {
      return t < 0.0 ? "N/A" : String.format("%.2f", Math.min(t, 20.0));
   }

   public static record EventContext(String playerName, String playerDisplay, String deathMessage, String chatMessage) {
   }
}
