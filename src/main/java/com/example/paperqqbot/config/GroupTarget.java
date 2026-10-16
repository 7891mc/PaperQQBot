package com.example.paperqqbot.config;

import java.util.Set;

public record GroupTarget(long groupId, String name, boolean enabled, Set<String> events) {
   public boolean acceptEvent(String eventName) {
      if (!this.enabled) {
         return false;
      } else {
         return this.events != null && !this.events.isEmpty() ? this.events.contains("*") || this.events.contains(eventName) : false;
      }
   }
}
