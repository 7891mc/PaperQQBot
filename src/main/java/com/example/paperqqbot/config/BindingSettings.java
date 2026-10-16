package com.example.paperqqbot.config;

public record BindingSettings(boolean enabled, String requestCommand, int codeExpireMinutes, boolean allowRebind, boolean requireBind) {
   public static BindingSettings fallback() {
      return new BindingSettings(true, "/绑定", 10, true, true);
   }

   public String normalizedRequestCommand() {
      return this.requestCommand == null ? "/绑定" : this.requestCommand.trim();
   }
}
