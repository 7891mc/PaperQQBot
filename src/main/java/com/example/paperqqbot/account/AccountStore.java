package com.example.paperqqbot.account;

import com.example.paperqqbot.PaperQQBotPlugin;
import java.io.File;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AccountStore {
   private static final SecureRandom RNG = new SecureRandom();
   private final PaperQQBotPlugin plugin;
   private final File file;
   private YamlConfiguration y;
   private final Set<String> sessions = ConcurrentHashMap.newKeySet();
   private String pendingEmail = "";
   private String pendingCode = "";
   private long pendingExpire = 0L;

   public AccountStore(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "web-account.yml");
      this.load();
   }

   private void load() {
      boolean changed = false;
      if (!this.file.exists()) {
         try {
            this.plugin.getDataFolder().mkdirs();
         } catch (Throwable var3) {
         }
      }

      this.y = YamlConfiguration.loadConfiguration(this.file);
      if (this.y.getString("instance-id", "").isBlank()) {
         this.y.set("instance-id", UUID.randomUUID().toString());
         changed = true;
      }

      if (this.y.getString("client-secret", "").isBlank()) {
         this.y.set("client-secret", randomHex(32));
         changed = true;
      }

      if (!this.y.contains("bound")) {
         this.y.set("bound", false);
         changed = true;
      }

      if (changed) {
         this.save();
      }
   }

   public String instanceId() {
      return this.y.getString("instance-id", "");
   }

   public String clientSecret() {
      return this.y.getString("client-secret", "");
   }

   public boolean isBound() {
      return this.y.getBoolean("bound", false);
   }

   public String accountId() {
      return this.y.getString("account-id", "");
   }

   public String email() {
      return this.y.getString("email", "");
   }

   public void markBound(String accountId, String email) {
      this.y.set("bound", true);
      this.y.set("account-id", accountId);
      this.y.set("email", email);
      this.y.set("bound-at", System.currentTimeMillis());
      this.save();
   }

   public void clearBound() {
      this.y.set("bound", false);
      this.y.set("account-id", "");
      this.y.set("email", "");
      this.y.set("bound-at", 0L);
      this.save();
      this.sessions.clear();
      this.clearMailPending();
   }

   public String createSession() {
      String token = randomHex(24);
      this.sessions.add(token);
      return token;
   }

   public boolean validSession(String token) {
      return token != null && this.sessions.contains(token);
   }

   public void removeSession(String token) {
      if (token != null) {
         this.sessions.remove(token);
      }
   }

   public void setMailPending(String email, String code, long ttlSeconds) {
      this.pendingEmail = email == null ? "" : email.toLowerCase();
      this.pendingCode = code == null ? "" : code;
      this.pendingExpire = System.currentTimeMillis() + ttlSeconds * 1000L;
   }

   public boolean checkMailPending(String email, String code) {
      if (System.currentTimeMillis() > this.pendingExpire) {
         this.clearMailPending();
         return false;
      } else {
         return this.pendingEmail.equalsIgnoreCase(email == null ? "" : email.trim()) && this.pendingCode.equals(code == null ? "" : code.trim());
      }
   }

   public void clearMailPending() {
      this.pendingEmail = "";
      this.pendingCode = "";
      this.pendingExpire = 0L;
   }

   private void save() {
      try {
         this.y.save(this.file);
      } catch (Exception var2) {
         this.plugin.pluginLogger().warning("[Account] 无法写入 web-account.yml：" + var2.getMessage());
      }
   }

   private static String randomHex(int bytes) {
      byte[] b = new byte[bytes];
      RNG.nextBytes(b);
      StringBuilder sb = new StringBuilder(bytes * 2);

      for (byte x : b) {
         sb.append(Character.forDigit(x >> 4 & 15, 16));
         sb.append(Character.forDigit(x & 15, 16));
      }

      return sb.toString();
   }
}
