package com.example.paperqqbot.bot;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.AtReplySettings;
import com.example.paperqqbot.config.BindingSettings;
import com.example.paperqqbot.config.SignInSettings;
import com.example.paperqqbot.core.InboundGroupMessage;
import com.example.paperqqbot.data.BindingStore;
import com.example.paperqqbot.data.SignInStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class AtReplyHandler {
   private static final int LLM_THREAD_POOL_SIZE = 4;
   private static final String EXECUTE_PREFIX = "/执行";
   private final PaperQQBotPlugin plugin;
   private final ConcurrentHashMap<String, Long> lastReplyEpoch = new ConcurrentHashMap<>();
   private final ExecutorService llmExecutor = Executors.newFixedThreadPool(4, r -> {
      Thread t = new Thread(r, "PaperQQBot-LlmChat");
      t.setDaemon(true);
      return t;
   });
   private volatile long learnedSelfId = 0L;
   private final ConversationMemory memory = new ConversationMemory();

   public AtReplyHandler(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   public long resolveSelfId(long configured, long eventSelf) {
      if (configured > 0L) {
         return configured;
      } else if (eventSelf > 0L) {
         if (this.learnedSelfId != eventSelf) {
            this.learnedSelfId = eventSelf;
            this.plugin.pluginLogger().info("[WsListener] 学习到机器人 self_id=" + eventSelf);
         }

         return eventSelf;
      } else {
         return 0L;
      }
   }

   public void handleInteraction(InboundGroupMessage msg) {
      boolean official = this.plugin.config().isOfficialMode();
      AtReplySettings cfg = this.plugin.config().getAtReply();
      boolean legacyAtReplyEnabled = official || cfg.enabled();
      String text = msg.text() == null ? "" : msg.text().trim();
      String groupKey = msg.groupKey();
      String identity = msg.identityKey();
      BindingSettings bindCfg = this.plugin.config().getBinding();
      String bindCmd = bindCfg.normalizedRequestCommand();
      if (bindCfg.enabled() && isPrefixCommand(text, bindCmd)) {
         this.handleBindCommand(msg, identity, text, bindCfg, bindCmd);
      } else {
         SignInSettings signCfg = this.plugin.config().getSignIn();
         if (signCfg.enabled() && text.equalsIgnoreCase(signCfg.normalizedCommand())) {
            this.handleSignIn(msg, identity, signCfg);
         } else {
            boolean isAdmin = msg.ownerRole() || !official && this.plugin.config().getAdmins().isAdmin(msg.userQq());
            if (isAdmin && isExecuteCommand(text)) {
               this.handleExecuteCommand(msg, text);
            } else if (legacyAtReplyEnabled) {
               String queryCmd = cfg.queryCommand() == null ? "/查询" : cfg.queryCommand().trim();
               if (text.equalsIgnoreCase(queryCmd)) {
                  if (this.checkCooldown(groupKey, cfg.cooldownMs())) {
                     this.reply(msg, this.buildServerInfoReply(msg));
                  }
               } else if (isAdmin && text.startsWith("/")) {
                  this.handleAdminCommand(msg, text);
               } else {
                  if (this.plugin.config().getLlm().enabled()) {
                     if (!this.checkCooldown(groupKey, cfg.cooldownMs())) {
                        return;
                     }

                     this.handleLlmChat(msg, text);
                  }
               }
            }
         }
      }
   }

   private static boolean isExecuteCommand(String text) {
      if (!text.regionMatches(true, 0, "/执行", 0, "/执行".length())) {
         return false;
      } else {
         return text.length() == "/执行".length() ? true : Character.isWhitespace(text.charAt("/执行".length()));
      }
   }

   private void handleBindCommand(InboundGroupMessage msg, String identity, String text, BindingSettings bindCfg, String bindCmd) {
      String arg = text.length() > bindCmd.length() ? text.substring(bindCmd.length()).trim() : "";
      if (arg.isEmpty()) {
         this.reply(msg, "用法：" + bindCmd + " 验证码\n请先在游戏内发送 /绑定QQ 获取仅你可见的验证码。");
      } else {
         BindingStore.RedeemResult result = this.plugin.bindingStore().redeemByOpenid(identity, arg, bindCfg.allowRebind());
         this.reply(msg, result.message());
         if (result.ok() && result.binding() != null) {
            this.plugin.bindingGate().notifyBound(result.binding().playerUuid());
         }
      }
   }

   private static boolean isPrefixCommand(String text, String prefix) {
      if (!text.regionMatches(true, 0, prefix, 0, prefix.length())) {
         return false;
      } else {
         return text.length() == prefix.length() ? true : Character.isWhitespace(text.charAt(prefix.length()));
      }
   }

   private void handleSignIn(InboundGroupMessage msg, String identity, SignInSettings signCfg) {
      BindingStore bindStore = this.plugin.bindingStore();
      BindingStore.Binding binding = bindStore.findBindingByOpenid(identity);
      if (binding == null) {
         this.reply(msg, "签到失败：你还没有绑定游戏账号。\n请先在群内发送 " + this.plugin.config().getBinding().normalizedRequestCommand() + " 获取验证码，再进服务器完成绑定。");
      } else if (bindStore.isBlacklisted(identity, binding.playerUuid(), binding.playerName())) {
         this.reply(msg, "签到失败：你已被加入黑名单，请联系管理员。");
      } else if (!this.plugin.economyAvailable()) {
         this.reply(msg, "签到失败：服务器未安装经济插件，该功能不可用。");
      } else {
         SignInStore signInStore = this.plugin.signInStore();
         if (signInStore.hasSignedToday(binding.playerUuid())) {
            this.reply(msg, "你今天已经签到过了，请明天再来~");
         } else {
            double reward = signCfg.reward();
            if (reward <= 0.0) {
               this.reply(msg, "签到奖励配置错误，请联系管理员。");
            } else {
               OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(binding.playerUuid());
               boolean deposited = this.plugin.vault().deposit(offlinePlayer, reward);
               if (!deposited) {
                  this.reply(msg, "签到失败：金币发放失败，请联系管理员。");
               } else {
                  SignInStore.Result result = signInStore.commit(binding.playerUuid(), binding.playerName());
                  this.reply(msg, "签到成功！玩家 " + binding.playerName() + " 获得 " + this.plugin.vault().format(reward) + "，已连续签到 " + result.streak() + " 天。");
               }
            }
         }
      }
   }

   private void reply(InboundGroupMessage msg, String text) {
      if (text != null && !text.isBlank()) {
         this.plugin.messageSender().replyToMessage(msg.groupKey(), text, msg.messageId());
      }
   }

   private boolean checkCooldown(String groupKey, long cooldownMs) {
      long now = System.currentTimeMillis();
      Long last = this.lastReplyEpoch.get(groupKey);
      if (last != null && now - last < cooldownMs) {
         return false;
      } else {
         this.lastReplyEpoch.put(groupKey, now);
         return true;
      }
   }

   private void handleAdminCommand(InboundGroupMessage msg, String text) {
      this.runConsoleCommand(msg, text.substring(1).trim());
   }

   private void handleExecuteCommand(InboundGroupMessage msg, String text) {
      String rest = text.substring("/执行".length()).trim();
      if (rest.startsWith("/")) {
         rest = rest.substring(1).trim();
      }

      if (rest.isEmpty()) {
         this.reply(msg, "用法：/执行 控制台指令\n例如：/执行 say 大家好");
      } else {
         this.runConsoleCommand(msg, rest);
      }
   }

   private void runConsoleCommand(InboundGroupMessage msg, String command) {
      if (command.isEmpty()) {
         this.reply(msg, "指令不能为空");
      } else {
         CountDownLatch latch = new CountDownLatch(1);
         List captured = Collections.synchronizedList(new ArrayList());
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
               this.dispatchAndCapture(command, captured);
            } catch (Throwable var8) {
               captured.add("指令执行异常：" + var8.getMessage());
            } finally {
               latch.countDown();
            }
         });

         try {
            if (!latch.await(10L, TimeUnit.SECONDS)) {
               this.reply(msg, "指令执行超时");
               return;
            }
         } catch (InterruptedException var6) {
            Thread.currentThread().interrupt();
            return;
         }

         Object output = String.join("\n", captured);
         if (((String)output).isBlank()) {
            output = "指令已执行（无输出）";
         }

         if (((String)output).length() > 2000) {
            output = ((String)output).substring(0, 2000) + "\n...（输出过长已截断）";
         }

         this.reply(msg, (String)output);
      }
   }

   private void dispatchAndCapture(String command, List<String> captured) {
      try (Log4jCapture capture = new Log4jCapture("PaperQQBotCapture")) {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

         for (String line : capture.lines()) {
            if (line != null && !line.contains("issued server command:")) {
               captured.add(line);
            }
         }
      }
   }

   private void handleLlmChat(InboundGroupMessage msg, String text) {
      String userInput = text.isEmpty() ? "你好" : text;
      int contextTurns = this.plugin.config().getLlm().contextTurns();
      String sessionKey = msg.groupKey() + "|" + msg.identityKey();
      List<ConversationMemory.Turn> history = this.memory.history(sessionKey, contextTurns);
      this.reply(msg, "思考中…");
      this.llmExecutor.submit(() -> {
         try {
            String r = this.plugin.llm().chat(userInput, history);
            if (r == null || r.isBlank()) {
               this.reply(msg, "（AI 暂时无法回复，请稍后再试）");
               return;
            }

            this.memory.appendExchange(sessionKey, userInput, r, contextTurns);
            this.reply(msg, r);
         } catch (Throwable var7) {
            this.plugin.pluginLogger().log(Level.WARNING, "[LLM] 回复失败：" + var7.getMessage(), var7);
            this.reply(msg, "（AI 回复出错）");
         }
      });
   }

   private String buildServerInfoReply(InboundGroupMessage msg) {
      AtReplySettings cfg = this.plugin.config().getAtReply();

      try {
         String s = this.plugin.placeholder().replace(cfg.template(), null);
         return s.replace("%sender_id%", msg.memberOpenid() != null ? msg.memberOpenid() : String.valueOf(msg.userQq()));
      } catch (Throwable var4) {
         this.plugin.pluginLogger().log(Level.WARNING, "[AtReply] 模板渲染失败：" + var4.getMessage(), var4);
         return "在线人数：" + this.plugin.serverInfo().onlinePlayers() + "/" + this.plugin.serverInfo().maxPlayers();
      }
   }

   public void shutdown() {
      try {
         this.llmExecutor.shutdown();
         if (!this.llmExecutor.awaitTermination(3L, TimeUnit.SECONDS)) {
            this.llmExecutor.shutdownNow();
         }
      } catch (InterruptedException var2) {
         Thread.currentThread().interrupt();
         this.llmExecutor.shutdownNow();
      }
   }

   public void resetCooldowns() {
      this.lastReplyEpoch.clear();
   }
}
