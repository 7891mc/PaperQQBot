package com.example.paperqqbot.config;

import java.util.Map;

public record Templates(Map<String, String> templates) {
   public String get(String eventName) {
      if (this.templates == null) {
         return "";
      } else {
         String v = this.templates.get(eventName);
         return v == null ? "" : v;
      }
   }

   public Map<String, String> all() {
      return this.templates == null ? Map.of() : this.templates;
   }
}
