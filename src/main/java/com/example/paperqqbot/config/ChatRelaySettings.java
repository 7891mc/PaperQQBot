package com.example.paperqqbot.config;

public record ChatRelaySettings(boolean groupToServer, String groupFormat) {
   public static ChatRelaySettings fallback() {
      return new ChatRelaySettings(true, "§7[QQ群] §f%name%: %message%");
   }

   public String format(String name, String message) {
      String f = this.groupFormat != null && !this.groupFormat.isBlank() ? this.groupFormat : fallback().groupFormat();
      return f.replace("%name%", name == null ? "QQ用户" : name).replace("%message%", message == null ? "" : message);
   }
}
