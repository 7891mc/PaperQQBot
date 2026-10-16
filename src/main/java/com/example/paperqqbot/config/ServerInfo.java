package com.example.paperqqbot.config;

public record ServerInfo(String name, String address, String contact, String extra, String website) {
   public static ServerInfo fallback() {
      return new ServerInfo("未配置", "unknown", "", "", "");
   }
}
