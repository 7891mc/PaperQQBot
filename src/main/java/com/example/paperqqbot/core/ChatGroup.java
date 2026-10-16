package com.example.paperqqbot.core;

import java.util.Set;

public record ChatGroup(String key, String name, boolean enabled, Set<String> events) {
   public boolean acceptEvent(String eventName) {
      if (!this.enabled) {
         return false;
      } else {
         return this.events != null && !this.events.isEmpty() ? this.events.contains("*") || this.events.contains(eventName) : false;
      }
   }
}
