package com.example.paperqqbot.config;

import java.util.Set;

public record Admins(Set<Long> qqNumbers) {
   public static Admins fallback() {
      return new Admins(Set.of());
   }

   public boolean isAdmin(long qq) {
      return this.qqNumbers != null && this.qqNumbers.contains(qq);
   }

   public boolean isEmpty() {
      return this.qqNumbers == null || this.qqNumbers.isEmpty();
   }
}
