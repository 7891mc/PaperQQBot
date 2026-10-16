package com.example.paperqqbot.serverinfo;

import com.example.paperqqbot.PaperQQBotPlugin;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ServerInfoProvider {
   private final PaperQQBotPlugin plugin;
   private volatile long startEpochMillis;
   private volatile boolean transitionedEmpty = false;

   public ServerInfoProvider(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.startEpochMillis = System.currentTimeMillis();
   }

   public void markStart() {
      this.startEpochMillis = System.currentTimeMillis();
   }

   public String minecraftVersion() {
      String bukkitVersion = Bukkit.getVersion();
      if (bukkitVersion == null) {
         return "unknown";
      } else {
         int dash = bukkitVersion.indexOf(45);
         return dash > 0 ? bukkitVersion.substring(0, dash) : bukkitVersion;
      }
   }

   public String bukkitVersion() {
      String v = Bukkit.getVersion();
      return v == null ? "unknown" : v;
   }

   public String serverIp() {
      try {
         return InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException var2) {
         return "unknown";
      }
   }

   public int serverPort() {
      return Bukkit.getPort();
   }

   public int onlinePlayers() {
      try {
         return Bukkit.getOnlinePlayers().size();
      } catch (Throwable var2) {
         return 0;
      }
   }

   public int maxPlayers() {
      return Bukkit.getMaxPlayers();
   }

   @NotNull
   public String playerList() {
      try {
         Collection<? extends Player> players = Bukkit.getOnlinePlayers();
         if (players.isEmpty()) {
            return "(无)";
         } else {
            ArrayList<String> names = new ArrayList<>(players.size());

            for (Player p : players) {
               names.add(p.getName());
            }

            return String.join(", ", names);
         }
      } catch (Throwable var5) {
         return "(不可用)";
      }
   }

   public double[] tps() {
      try {
         return Bukkit.getTPS();
      } catch (Throwable var2) {
         return new double[]{-1.0, -1.0, -1.0};
      }
   }

   public long memoryUsedMb() {
      Runtime rt = Runtime.getRuntime();
      long used = rt.totalMemory() - rt.freeMemory();
      return used / 1048576L;
   }

   public long memoryMaxMb() {
      return Runtime.getRuntime().maxMemory() / 1048576L;
   }

   public long uptimeSeconds() {
      return (System.currentTimeMillis() - this.startEpochMillis) / 1000L;
   }

   public String uptimeFormatted() {
      long s = this.uptimeSeconds();
      long d = s / 86400L;
      long h = s % 86400L / 3600L;
      long m = s % 3600L / 60L;
      long sec = s % 60L;
      StringBuilder sb = new StringBuilder();
      if (d > 0L) {
         sb.append(d).append("d ");
      }

      if (h > 0L || d > 0L) {
         sb.append(h).append("h ");
      }

      if (m > 0L || h > 0L || d > 0L) {
         sb.append(m).append("m ");
      }

      sb.append(sec).append("s");
      return sb.toString();
   }

   public boolean isEmpty() {
      return this.onlinePlayers() == 0;
   }

   public void setTransitionedEmpty(boolean v) {
      this.transitionedEmpty = v;
   }

   public boolean isTransitionedEmpty() {
      return this.transitionedEmpty;
   }

   public String motd() {
      String motd;
      try {
         motd = Bukkit.getMotd();
      } catch (Throwable var3) {
         motd = "";
      }

      if (motd == null) {
         motd = "";
      }

      return motd.replaceAll("(?i)§[0-9A-FK-OR]", "");
   }

   public PaperQQBotPlugin plugin() {
      return this.plugin;
   }
}
