package com.example.paperqqbot.config;

import java.util.Map;

public record EventToggles(Map<String, Boolean> toggles) {
   public boolean isEnabled(String eventName) {
      if (this.toggles == null) {
         return false;
      } else {
         Boolean v = this.toggles.get(eventName);
         return v != null && v;
      }
   }
}
