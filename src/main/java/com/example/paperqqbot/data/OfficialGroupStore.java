package com.example.paperqqbot.data;

import com.example.paperqqbot.core.ChatGroup;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class OfficialGroupStore {
   private final File file;
   private final Object lock = new Object();
   private YamlConfiguration yaml;

   public OfficialGroupStore(File dataFolder) {
      dataFolder.mkdirs();
      this.file = new File(dataFolder, "official-groups.yml");
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

   public void learn(String openid, String name) {
      Object object = this.lock;
      synchronized (object) {
         if (openid != null && !openid.isBlank()) {
            ArrayList<Map<String, Object>> raw = new ArrayList<>();

            for (Map<?, ?> m : this.yaml.getMapList("groups")) {
               raw.add((Map<String, Object>)m);
            }

            for (Map<String, Object> m : raw) {
               if (openid.equals(String.valueOf(m.get("openid")))) {
                  return;
               }
            }

            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("openid", openid);
            entry.put("note", name == null ? "" : name);
            entry.put("enabled", Boolean.valueOf(true));
            entry.put("events", List.of("*"));
            raw.add(entry);
            this.yaml.set("groups", raw);
            this.save();
         }
      }
   }

   public List<ChatGroup> groups() {
      Object object = this.lock;
      synchronized (object) {
         ArrayList<ChatGroup> result = new ArrayList<>();

         for (Map<?, ?> m : this.yaml.getMapList("groups")) {
            Object openidObj = m.get("openid");
            String openid = openidObj == null ? "" : String.valueOf(openidObj);
            Object noteObj = m.get("note");
            String note = noteObj == null ? "" : String.valueOf(noteObj);
            Object enabledObj = m.get("enabled");
            boolean enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));
            LinkedHashSet<String> events = new LinkedHashSet<>();
            Object ev = m.get("events");
            if (ev instanceof List) {
               for (Object o : (List)ev) {
                  if (o != null && !String.valueOf(o).isBlank()) {
                     events.add(String.valueOf(o));
                  }
               }
            }

            result.add(new ChatGroup(openid, note, enabled, events));
         }

         return result;
      }
   }

   public void replaceAll(List<ChatGroup> groups) {
      Object object = this.lock;
      synchronized (object) {
         ArrayList raw = new ArrayList();

         for (ChatGroup g : groups) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("openid", g.key());
            entry.put("note", g.name() == null ? "" : g.name());
            entry.put("enabled", Boolean.valueOf(g.enabled()));
            entry.put("events", new ArrayList<>(g.events()));
            raw.add(entry);
         }

         this.yaml.set("groups", raw);
         this.save();
      }
   }
}
