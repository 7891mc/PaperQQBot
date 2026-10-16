package com.example.paperqqbot.data;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SignInStore {
   private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
   private final File file;
   private final Object lock = new Object();
   private YamlConfiguration yaml;

   public SignInStore(File dataFolder) {
      dataFolder.mkdirs();
      this.file = new File(dataFolder, "signin.yml");
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

   private static String today() {
      return LocalDate.now(ZONE).toString();
   }

   public boolean hasSignedToday(UUID uuid) {
      Object object = this.lock;
      synchronized (object) {
         return today().equals(this.yaml.getString(uuid + ".last-date", ""));
      }
   }

   public int streak(UUID uuid) {
      Object object = this.lock;
      synchronized (object) {
         return this.yaml.getInt(uuid + ".streak", 0);
      }
   }

   public int total(UUID uuid) {
      Object object = this.lock;
      synchronized (object) {
         return this.yaml.getInt(uuid + ".total", 0);
      }
   }

   public SignInStore.Result commit(UUID uuid, String name) {
      Object object = this.lock;
      synchronized (object) {
         String today = today();
         String lastDate = this.yaml.getString(uuid + ".last-date", "");
         int lastStreak = this.yaml.getInt(uuid + ".streak", 0);
         LocalDate last;
         int streak = lastDate.isEmpty() ? 1 : ((last = parseDate(lastDate)) != null && last.plusDays(1L).toString().equals(today) ? lastStreak + 1 : 1);
         int total = this.yaml.getInt(uuid + ".total", 0) + 1;
         this.yaml.set(uuid + ".last-date", today);
         this.yaml.set(uuid + ".streak", streak);
         this.yaml.set(uuid + ".total", total);
         if (name != null && !name.isBlank()) {
            this.yaml.set(uuid + ".name", name);
         }

         this.save();
         return new SignInStore.Result(streak, total, today);
      }
   }

   public List<Map<String, Object>> list() {
      Object object = this.lock;
      synchronized (object) {
         ArrayList<Map<String, Object>> result = new ArrayList<>();

         for (String uuid : this.yaml.getKeys(false)) {
            if (this.yaml.isConfigurationSection(uuid)) {
               LinkedHashMap<String, Object> m = new LinkedHashMap<>();
               m.put("uuid", uuid);
               m.put("name", this.yaml.getString(uuid + ".name", ""));
               m.put("lastDate", this.yaml.getString(uuid + ".last-date", ""));
               m.put("streak", Integer.valueOf(this.yaml.getInt(uuid + ".streak", 0)));
               m.put("total", Integer.valueOf(this.yaml.getInt(uuid + ".total", 0)));
               result.add(m);
            }
         }

         return result;
      }
   }

   private static LocalDate parseDate(String s) {
      try {
         return LocalDate.parse(s);
      } catch (Exception var2) {
         return null;
      }
   }

   public static record Result(int streak, int total, String date) {
   }
}
