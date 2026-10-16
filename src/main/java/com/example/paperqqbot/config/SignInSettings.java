package com.example.paperqqbot.config;

public record SignInSettings(boolean enabled, String command, double reward) {
   public static SignInSettings fallback() {
      return new SignInSettings(true, "/签到", 100.0);
   }

   public String normalizedCommand() {
      return this.command == null ? "/签到" : this.command.trim();
   }
}
