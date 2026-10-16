package com.example.paperqqbot.config;

public record AtReplySettings(boolean enabled, long selfId, String template, long cooldownMs, String queryCommand) {
   public static AtReplySettings fallback() {
      return new AtReplySettings(false, 0L, "当前服务器在线：%online_players%/%max_players%\n在线玩家：%player_list%", 3000L, "/查询");
   }
}
