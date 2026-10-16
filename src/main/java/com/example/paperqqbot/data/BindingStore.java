package com.example.paperqqbot.data;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BindingStore {
   private static final SecureRandom RANDOM = new SecureRandom();
   private final File file;
   private final Object lock = new Object();
   private YamlConfiguration yaml;

   public BindingStore(File dataFolder) {
      dataFolder.mkdirs();
      this.file = new File(dataFolder, "bindings.yml");
      this.load();
   }

   private void load() {
      Object object = this.lock;
      synchronized (object) {
         this.yaml = new YamlConfiguration();
         if (this.file.exists()) {
            try {
               this.yaml.load(this.file);
            } catch (Exception var5) {
               this.yaml = new YamlConfiguration();
            }
         }
      }
   }

   private void save() {
      Object object = this.lock;
      synchronized (object) {
         try {
            this.yaml.save(this.file);
         } catch (Exception var5) {
         }
      }
   }

   public String requestBindCodeByPlayer(UUID playerUuid, String playerName, boolean allowRebind, int expireMinutes) {
      Object object = this.lock;
      synchronized (object) {
         if (this.isBlacklistedLocked(null, playerUuid, playerName)) {
            return "你已被加入黑名单，无法申请绑定，请联系管理员。";
         } else {
            BindingStore.Binding existing = this.findBindingByPlayerLocked(playerUuid);
            if (existing != null && !allowRebind) {
               return "该游戏账号已绑定 QQ，服务器未开放重新绑定。";
            } else {
               int minutes = Math.max(1, expireMinutes);
               String code = this.createCodeLocked(playerUuid, playerName, minutes);
               StringBuilder sb = new StringBuilder();
               sb.append("你的 QQ 绑定验证码是：").append(code).append('\n');
               sb.append("请加入 QQ 群，@机器人 并发送 /绑定 ").append(code).append(" 完成绑定。\n");
               sb.append("验证码 ").append(minutes).append(" 分钟内有效（仅你本人可见）");
               if (existing != null) {
                  sb.append("\n（你此前已绑定，本次将自动换绑）");
               }

               return sb.toString();
            }
         }
      }
   }

   public String autoIssueOnJoin(UUID playerUuid, String playerName, boolean allowRebind, int expireMinutes) {
      Object object = this.lock;
      synchronized (object) {
         int minutes = Math.max(1, expireMinutes);
         ConfigurationSection c = this.peekValidCodeLocked(playerUuid);
         if (c != null) {
            long remainMs = c.getLong("expire-at", 0L) - System.currentTimeMillis();
            long remainMin = Math.max(1L, (remainMs + 59999L) / 60000L);
            String code = c.getString("code", "");
            StringBuilder sb = new StringBuilder();
            sb.append("【请先绑定 QQ】\n");
            sb.append("你之前的验证码仍有效：").append(code).append('\n');
            sb.append("请加入 QQ 群，@机器人 并发送 /绑定 ").append(code).append(" 完成绑定。\n");
            sb.append("请在剩余 ").append(remainMin).append(" 分钟内完成，否则将被移出服务器。");
            return sb.toString();
         } else {
            BindingStore.Binding existing = this.findBindingByPlayerLocked(playerUuid);
            if (existing != null && !allowRebind) {
               return "该游戏账号已绑定 QQ。";
            } else {
               String code = this.createCodeLocked(playerUuid, playerName, minutes);
               StringBuilder sb = new StringBuilder();
               sb.append("【请先绑定 QQ】\n");
               sb.append("你的绑定验证码是：").append(code).append('\n');
               sb.append("请加入 QQ 群，@机器人 并发送 /绑定 ").append(code).append(" 完成绑定。\n");
               sb.append("未在 ").append(minutes).append(" 分钟内完成绑定，将被移出服务器。");
               return sb.toString();
            }
         }
      }
   }

   public Long getCodeExpireAt(UUID playerUuid) {
      Object object = this.lock;
      synchronized (object) {
         ConfigurationSection c = this.peekValidCodeLocked(playerUuid);
         return c == null ? null : c.getLong("expire-at", 0L);
      }
   }

   private String createCodeLocked(UUID playerUuid, String playerName, int expireMinutes) {
      String code = generateCode();
      String key = playerUuid.toString();
      ConfigurationSection codes = section(this.yaml, "codes");
      codes.set(key + ".code", code);
      codes.set(key + ".expire-at", System.currentTimeMillis() + (long)Math.max(1, expireMinutes) * 60000L);
      codes.set(key + ".name", playerName);
      this.save();
      return code;
   }

   private ConfigurationSection peekValidCodeLocked(UUID playerUuid) {
      ConfigurationSection codes = this.yaml.getConfigurationSection("codes");
      if (codes == null) {
         return null;
      } else {
         ConfigurationSection c = codes.getConfigurationSection(playerUuid.toString());
         if (c == null) {
            return null;
         } else {
            long expire = c.getLong("expire-at", 0L);
            return expire > 0L && expire < System.currentTimeMillis() ? null : c;
         }
      }
   }

   public BindingStore.RedeemResult redeemByOpenid(String openid, String code, boolean allowRebind) {
      Object object = this.lock;
      synchronized (object) {
         if (code != null && !code.isBlank()) {
            code = code.trim();
            ConfigurationSection codes = section(this.yaml, "codes");
            String matchedUuid = null;
            String matchedName = null;
            long now = System.currentTimeMillis();

            for (String key : codes.getKeys(false)) {
               ConfigurationSection c = codes.getConfigurationSection(key);
               if (c != null && code.equalsIgnoreCase(c.getString("code"))) {
                  long expire = c.getLong("expire-at", 0L);
                  if (expire > 0L && expire < now) {
                     return new BindingStore.RedeemResult(false, "验证码已过期，请在游戏内重新发送 /绑定QQ。", null);
                  }

                  try {
                     matchedUuid = UUID.fromString(key).toString();
                     matchedName = c.getString("name", "");
                  } catch (IllegalArgumentException var18) {
                  }
                  break;
               }
            }

            if (matchedUuid == null) {
               return new BindingStore.RedeemResult(false, "验证码无效，请确认输入正确。", null);
            } else {
               UUID playerUuid = UUID.fromString(matchedUuid);
               if (this.isBlacklistedLocked(openid, playerUuid, matchedName)) {
                  return new BindingStore.RedeemResult(false, "你已被加入黑名单，无法绑定，请联系管理员。", null);
               } else {
                  BindingStore.Binding openidBinding = this.findBindingByOpenidLocked(openid);
                  if (openidBinding != null && !openidBinding.playerUuid().equals(playerUuid) && !allowRebind) {
                     return new BindingStore.RedeemResult(false, "你已绑定游戏玩家 " + openidBinding.playerName() + "，服务器未开放重新绑定。", null);
                  } else {
                     ConfigurationSection bindings = section(this.yaml, "bindings");
                     BindingStore.Binding playerBinding = this.findBindingByPlayerLocked(playerUuid);
                     if (playerBinding != null && !playerBinding.openid().equals(openid)) {
                        if (!allowRebind) {
                           return new BindingStore.RedeemResult(false, "该游戏账号已绑定其他 QQ，未开放换绑。", null);
                        }

                        bindings.set(playerBinding.openid(), null);
                     }

                     bindings.set(openid + ".player-uuid", matchedUuid);
                     bindings.set(openid + ".player-name", matchedName);
                     bindings.set(openid + ".bound-at", now);
                     codes.set(matchedUuid, null);
                     this.save();
                     BindingStore.Binding binding = new BindingStore.Binding(openid, playerUuid, matchedName, now);
                     return new BindingStore.RedeemResult(true, "绑定成功！游戏账号 " + matchedName + " 已与你的 QQ 绑定。", binding);
                  }
               }
            }
         } else {
            return new BindingStore.RedeemResult(false, "用法：/绑定 验证码（请先在游戏内发送 /绑定QQ 获取验证码）", null);
         }
      }
   }

   public String unbindByPlayer(UUID playerUuid) {
      Object object = this.lock;
      synchronized (object) {
         BindingStore.Binding b = this.findBindingByPlayerLocked(playerUuid);
         if (b == null) {
            return "你当前没有绑定任何 QQ。";
         } else {
            section(this.yaml, "bindings").set(b.openid(), null);
            this.save();
            return "已解除与 QQ 的绑定，可重新申请绑定。";
         }
      }
   }

   public boolean adminUnbind(String playerName) {
      Object object = this.lock;
      synchronized (object) {
         ConfigurationSection bindings = section(this.yaml, "bindings");

         for (String openid : new ArrayList(bindings.getKeys(false))) {
            ConfigurationSection b = bindings.getConfigurationSection(openid);
            if (b != null && playerName.equalsIgnoreCase(b.getString("player-name"))) {
               bindings.set(openid, null);
               this.save();
               return true;
            }
         }

         return false;
      }
   }

   public BindingStore.Binding findBindingByOpenid(String openid) {
      Object object = this.lock;
      synchronized (object) {
         return this.findBindingByOpenidLocked(openid);
      }
   }

   public BindingStore.Binding findBindingByPlayer(UUID uuid) {
      Object object = this.lock;
      synchronized (object) {
         return this.findBindingByPlayerLocked(uuid);
      }
   }

   private BindingStore.Binding findBindingByOpenidLocked(String openid) {
      ConfigurationSection bindings = this.yaml.getConfigurationSection("bindings");
      if (bindings == null) {
         return null;
      } else {
         ConfigurationSection b = bindings.getConfigurationSection(openid);
         return toBinding(openid, b);
      }
   }

   private BindingStore.Binding findBindingByPlayerLocked(UUID uuid) {
      ConfigurationSection bindings = this.yaml.getConfigurationSection("bindings");
      if (bindings != null && uuid != null) {
         for (String openid : bindings.getKeys(false)) {
            ConfigurationSection b = bindings.getConfigurationSection(openid);
            if (b != null && uuid.toString().equalsIgnoreCase(b.getString("player-uuid"))) {
               return toBinding(openid, b);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static BindingStore.Binding toBinding(String openid, ConfigurationSection b) {
      if (b == null) {
         return null;
      } else {
         UUID uuid;
         try {
            uuid = UUID.fromString(b.getString("player-uuid", ""));
         } catch (IllegalArgumentException var4) {
            return null;
         }

         return new BindingStore.Binding(openid, uuid, b.getString("player-name", ""), b.getLong("bound-at", 0L));
      }
   }

   public List<BindingStore.Binding> listBindings() {
      Object object = this.lock;
      synchronized (object) {
         ArrayList<BindingStore.Binding> list = new ArrayList<>();
         ConfigurationSection bindings = this.yaml.getConfigurationSection("bindings");
         if (bindings != null) {
            for (String openid : bindings.getKeys(false)) {
               BindingStore.Binding b = toBinding(openid, bindings.getConfigurationSection(openid));
               if (b != null) {
                  list.add(b);
               }
            }
         }

         return list;
      }
   }

   public boolean addBlacklist(String entry) {
      Object object = this.lock;
      synchronized (object) {
         List<String> list = this.blacklistLocked();

         for (String e : list) {
            if (e.equalsIgnoreCase(entry)) {
               return false;
            }
         }

         list.add(entry);
         this.yaml.set("blacklist", list);
         this.save();
         return true;
      }
   }

   public boolean removeBlacklist(String entry) {
      Object object = this.lock;
      synchronized (object) {
         List<String> list = this.blacklistLocked();
         boolean removed = list.removeIf(e -> e.equalsIgnoreCase(entry));
         if (removed) {
            this.yaml.set("blacklist", list);
            this.save();
         }

         return removed;
      }
   }

   public List<String> listBlacklist() {
      Object object = this.lock;
      synchronized (object) {
         return new ArrayList<>(this.blacklistLocked());
      }
   }

   public boolean isBlacklisted(String openid, UUID playerUuid, String playerName) {
      Object object = this.lock;
      synchronized (object) {
         return this.isBlacklistedLocked(openid, playerUuid, playerName);
      }
   }

   private boolean isBlacklistedLocked(String openid, UUID playerUuid, String playerName) {
      for (String e : this.blacklistLocked()) {
         if (openid != null && e.equalsIgnoreCase(openid)) {
            return true;
         }

         if (playerName != null && e.equalsIgnoreCase(playerName)) {
            return true;
         }

         if (playerUuid != null && e.equalsIgnoreCase(playerUuid.toString())) {
            return true;
         }
      }

      return false;
   }

   private List<String> blacklistLocked() {
      ArrayList<String> list = new ArrayList<>();
      List raw = this.yaml.getList("blacklist");
      if (raw != null) {
         for (Object o : raw) {
            if (o != null && !String.valueOf(o).isBlank()) {
               list.add(String.valueOf(o));
            }
         }
      }

      return list;
   }

   private static ConfigurationSection section(YamlConfiguration y, String path) {
      ConfigurationSection sec = y.getConfigurationSection(path);
      if (sec == null) {
         sec = y.createSection(path);
      }

      return sec;
   }

   private static String generateCode() {
      StringBuilder sb = new StringBuilder(6);

      for (int i = 0; i < 6; i++) {
         sb.append(RANDOM.nextInt(10));
      }

      return sb.toString();
   }

   public List<Map<String, Object>> bindingsForWeb() {
      Object object = this.lock;
      synchronized (object) {
         ArrayList<Map<String, Object>> result = new ArrayList<>();

         for (BindingStore.Binding b : this.listBindings()) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("openid", b.openid());
            m.put("player", b.playerName());
            m.put("playerUuid", b.playerUuid().toString());
            m.put("boundAt", Long.valueOf(b.boundAt()));
            result.add(m);
         }

         return result;
      }
   }

   public static record Binding(String openid, UUID playerUuid, String playerName, long boundAt) {
   }

   public static record RedeemResult(boolean ok, String message, BindingStore.Binding binding) {
   }
}
