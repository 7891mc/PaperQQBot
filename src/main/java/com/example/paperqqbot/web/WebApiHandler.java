package com.example.paperqqbot.web;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.account.AccountClient;
import com.example.paperqqbot.config.GroupTarget;
import com.example.paperqqbot.config.PluginConfig;
import com.example.paperqqbot.core.ChatGroup;
import com.example.paperqqbot.data.BindingStore;
import com.example.paperqqbot.placeholder.PlaceholderReplacer;
import com.example.paperqqbot.serverinfo.ServerInfoProvider;
import com.example.paperqqbot.util.SimpleJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

final class WebApiHandler implements HttpHandler {
   private static final List<String> EVENT_NAMES = List.of("start", "stop", "join", "quit", "empty", "chat", "death", "tps-warning");
   private final PaperQQBotPlugin plugin;

   WebApiHandler(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      try {
         String method = exchange.getRequestMethod();
         String path = exchange.getRequestURI().getPath();
         if (path.startsWith("/api/auth/")) {
            this.handleAuth(exchange, path);
            return;
         }

         if (!this.checkAuth(exchange)) {
            sendJson(exchange, 401, Map.of("ok", false, "error", "未登录或会话已失效"));
            return;
         }

         switch (path) {
            case "/api/status":
               this.handleStatus(exchange);
               break;
            case "/api/config":
               if ("GET".equals(method)) {
                  this.handleGetConfig(exchange);
               } else if ("POST".equals(method)) {
                  this.handlePostConfig(exchange);
               } else {
                  sendJson(exchange, 405, Map.of("ok", false, "error", "Method Not Allowed"));
               }
               break;
            case "/api/test":
               this.handleTest(exchange);
               break;
            case "/api/reload":
               this.handleReload(exchange);
               break;
            case "/api/manage":
               this.handleManage(exchange);
               break;
            default:
               sendJson(exchange, 404, Map.of("ok", false, "error", "Not Found"));
         }
      } catch (Throwable var7) {
         Throwable t = var7;
         this.plugin.pluginLogger().log(Level.WARNING, "[WebApi] 处理请求失败：" + var7.getMessage(), var7);

         try {
            sendJson(exchange, 500, Map.of("ok", false, "error", "服务器内部错误：" + t.getMessage()));
         } catch (IOException var6) {
         }
      }
   }

   private boolean checkAuth(HttpExchange exchange) {
      String auth = exchange.getRequestHeaders().getFirst("Authorization");
      return auth != null && auth.startsWith("Bearer ") ? this.plugin.accountStore().validSession(auth.substring("Bearer ".length()).trim()) : false;
   }

   private void handleAuth(HttpExchange exchange, String path) throws IOException {
      switch (path) {
         case "/api/auth/state":
            this.authState(exchange);
            break;
         case "/api/auth/bind":
            this.authBind(exchange);
            break;
         case "/api/auth/login":
            this.authLogin(exchange);
            break;
         case "/api/auth/mailcode":
            this.authMailCode(exchange);
            break;
         case "/api/auth/verify":
            this.authVerify(exchange);
            break;
         case "/api/auth/logout":
            this.authLogout(exchange);
            break;
         default:
            sendJson(exchange, 404, Map.of("ok", false, "error", "Not Found"));
      }
   }

   private void authState(HttpExchange exchange) throws IOException {
      LinkedHashMap<String, Object> d = new LinkedHashMap<>();
      d.put("bound", Boolean.valueOf(this.plugin.accountStore().isBound()));
      d.put("email", this.plugin.accountStore().email());
      sendJson(exchange, 200, Map.of("ok", true, "data", d));
   }

   private void authBind(HttpExchange exchange) throws IOException {
      if (this.plugin.accountStore().isBound()) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "本实例已绑定，请先在官网控制台解绑"));
      } else {
         Map<String, Object> req = SimpleJson.parseObject(readBody(exchange));
         String code = str(req, "code");
         if (code.isBlank()) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "请输入绑定码"));
         } else {
            AccountClient.Result r = this.plugin.accountClient().bind(code);
            if (!r.ok) {
               sendJson(exchange, 400, Map.of("ok", false, "error", r.error));
            } else {
               String accountId = r.str("accountId", "");
               String email = r.str("email", "");
               if (!accountId.isBlank() && !email.isBlank()) {
                  this.plugin.accountStore().markBound(accountId, email);
                  String token = this.plugin.accountStore().createSession();
                  LinkedHashMap<String, String> d = new LinkedHashMap<>();
                  d.put("token", token);
                  d.put("email", email);
                  sendJson(exchange, 200, Map.of("ok", true, "data", d));
               } else {
                  sendJson(exchange, 400, Map.of("ok", false, "error", "绑定响应无效"));
               }
            }
         }
      }
   }

   private void authLogin(HttpExchange exchange) throws IOException {
      if (!this.plugin.accountStore().isBound()) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "请先完成绑定"));
      } else {
         Map<String, Object> req = SimpleJson.parseObject(readBody(exchange));
         String email = str(req, "email");
         String password = str(req, "password");
         if (!email.isBlank() && !password.isBlank()) {
            AccountClient.Result r = this.plugin.accountClient().authPassword(email, password);
            if (!r.ok) {
               sendJson(exchange, 401, Map.of("ok", false, "error", r.error));
            } else if (!email.equalsIgnoreCase(this.plugin.accountStore().email())) {
               sendJson(exchange, 403, Map.of("ok", false, "error", "该账号未绑定本实例，仅绑定账号可登录"));
            } else {
               String token = this.plugin.accountStore().createSession();
               LinkedHashMap<String, String> d = new LinkedHashMap<>();
               d.put("token", token);
               d.put("email", this.plugin.accountStore().email());
               sendJson(exchange, 200, Map.of("ok", true, "data", d));
            }
         } else {
            sendJson(exchange, 400, Map.of("ok", false, "error", "请输入邮箱和密码"));
         }
      }
   }

   private void authMailCode(HttpExchange exchange) throws IOException {
      if (!this.plugin.accountStore().isBound()) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "请先完成绑定"));
      } else {
         Map<String, Object> req = SimpleJson.parseObject(readBody(exchange));
         String email = str(req, "email");
         if (email.isBlank()) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "请输入邮箱"));
         } else if (!email.equalsIgnoreCase(this.plugin.accountStore().email())) {
            sendJson(exchange, 403, Map.of("ok", false, "error", "该账号未绑定本实例"));
         } else {
            AccountClient.Result r = this.plugin.accountClient().mailCode(email);
            if (!r.ok) {
               sendJson(exchange, 400, Map.of("ok", false, "error", r.error));
            } else {
               String code = r.str("code", "");
               long ttl = 300L;
               if (r.secure.get("expiresIn") instanceof Number n) {
                  ttl = n.longValue();
               }

               if (!code.isBlank()) {
                  this.plugin.accountStore().setMailPending(email, code, ttl);
               }

               LinkedHashMap<String, Object> d = new LinkedHashMap<>();
               d.put("sent", Boolean.valueOf(r.bool("sent", false)));
               d.put("expiresIn", Long.valueOf(ttl));
               String error = r.str("error", "");
               if (!error.isBlank()) {
                  d.put("error", error);
               }

               sendJson(exchange, 200, Map.of("ok", true, "data", d));
            }
         }
      }
   }

   private void authVerify(HttpExchange exchange) throws IOException {
      if (!this.plugin.accountStore().isBound()) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "请先完成绑定"));
      } else {
         Map<String, Object> req = SimpleJson.parseObject(readBody(exchange));
         String email = str(req, "email");
         String code = str(req, "code");
         if (!this.plugin.accountStore().checkMailPending(email, code)) {
            sendJson(exchange, 401, Map.of("ok", false, "error", "验证码错误或已过期"));
         } else {
            this.plugin.accountStore().clearMailPending();
            String token = this.plugin.accountStore().createSession();
            LinkedHashMap<String, String> d = new LinkedHashMap<>();
            d.put("token", token);
            d.put("email", this.plugin.accountStore().email());
            sendJson(exchange, 200, Map.of("ok", true, "data", d));
         }
      }
   }

   private void authLogout(HttpExchange exchange) throws IOException {
      String auth = exchange.getRequestHeaders().getFirst("Authorization");
      if (auth != null && auth.startsWith("Bearer ")) {
         this.plugin.accountStore().removeSession(auth.substring("Bearer ".length()).trim());
      }

      sendJson(exchange, 200, Map.of("ok", true));
   }

   private void handleStatus(HttpExchange exchange) throws IOException {
      LinkedHashMap<String, Object> data = new LinkedHashMap<>();
      ServerInfoProvider info = this.plugin.serverInfo();
      data.put("serverName", this.plugin.config().getServer().name());
      data.put("onlinePlayers", Integer.valueOf(info.onlinePlayers()));
      data.put("maxPlayers", Integer.valueOf(info.maxPlayers()));
      double[] tps = info.tps();
      data.put("tps1m", Double.valueOf(round2(tps.length > 0 ? tps[0] : -1.0)));
      data.put("tps5m", Double.valueOf(round2(tps.length > 1 ? tps[1] : -1.0)));
      data.put("tps15m", Double.valueOf(round2(tps.length > 2 ? tps[2] : -1.0)));
      data.put("memoryUsedMb", Long.valueOf(info.memoryUsedMb()));
      data.put("memoryMaxMb", Long.valueOf(info.memoryMaxMb()));
      data.put("uptimeFormatted", info.uptimeFormatted());
      data.put("playerList", info.playerList());
      data.put("mode", this.plugin.config().getMode());
      data.put("runtimeConnected", Boolean.valueOf(this.plugin.runtimeConnected()));
      data.put("wsEnabled", Boolean.valueOf(this.plugin.config().getWs().enabled()));
      data.put("atReplyEnabled", Boolean.valueOf(this.plugin.config().getAtReply().enabled()));
      data.put("groups", Integer.valueOf(this.plugin.chatGroups().size()));
      data.put("bindings", Integer.valueOf(this.plugin.bindingStore().listBindings().size()));
      sendJson(exchange, 200, Map.of("ok", true, "data", data));
   }

   private void handleGetConfig(HttpExchange exchange) throws IOException {
      PluginConfig cfg = this.plugin.config();
      LinkedHashMap<String, Object> data = new LinkedHashMap<>();
      data.put("mode", cfg.getMode());
      LinkedHashMap<String, String> official = new LinkedHashMap<>();
      official.put("appId", cfg.getOfficialBot().appId());
      official.put("secret", maskSecret(cfg.getOfficialBot().secret()));
      data.put("officialBot", official);
      LinkedHashMap<String, Object> bot = new LinkedHashMap<>();
      bot.put("httpUrl", cfg.getBot().httpUrl());
      bot.put("accessToken", maskSecret(cfg.getBot().accessToken()));
      bot.put("retry", Integer.valueOf(cfg.getBot().retry()));
      bot.put("debug", Boolean.valueOf(cfg.getBot().debug()));
      data.put("bot", bot);
      LinkedHashMap<String, String> server = new LinkedHashMap<>();
      server.put("name", cfg.getServer().name());
      server.put("address", cfg.getServer().address());
      server.put("contact", cfg.getServer().contact());
      server.put("extra", cfg.getServer().extra());
      server.put("website", cfg.getServer().website());
      data.put("server", server);
      ArrayList groups = new ArrayList();

      for (GroupTarget groupTarget : cfg.getGroups()) {
         LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<>();
         linkedHashMap.put("groupId", Long.valueOf(groupTarget.groupId()));
         linkedHashMap.put("name", groupTarget.name());
         linkedHashMap.put("enabled", Boolean.valueOf(groupTarget.enabled()));
         linkedHashMap.put("events", String.join(",", groupTarget.events()));
         groups.add(linkedHashMap);
      }

      data.put("groups", groups);
      ArrayList<Map<String, Object>> officialGroups = new ArrayList<>();

      for (ChatGroup chatGroup : this.plugin.officialGroups().groups()) {
         LinkedHashMap<String, Object> gm = new LinkedHashMap<>();
         gm.put("openid", chatGroup.key());
         gm.put("note", chatGroup.name());
         gm.put("enabled", Boolean.valueOf(chatGroup.enabled()));
         gm.put("events", String.join(",", chatGroup.events()));
         officialGroups(gm, officialGroups);
      }

      data.put("officialGroups", officialGroups);
      LinkedHashMap<String, Boolean> linkedHashMap = new LinkedHashMap<>();

      for (String k : EVENT_NAMES) {
         linkedHashMap.put(k, Boolean.valueOf(cfg.getEventToggles().isEnabled(k)));
      }

      data.put("eventToggles", linkedHashMap);
      LinkedHashMap<String, Object> linkedHashMap2 = new LinkedHashMap<>();
      linkedHashMap2.put("enabled", Boolean.valueOf(cfg.getWs().enabled()));
      linkedHashMap2.put("url", cfg.getWs().url());
      linkedHashMap2.put("reconnectMs", Long.valueOf(cfg.getWs().reconnectMs()));
      linkedHashMap2.put("accessToken", maskSecret(cfg.getWs().accessToken()));
      data.put("ws", linkedHashMap2);
      LinkedHashMap<String, Object> at = new LinkedHashMap<>();
      at.put("enabled", Boolean.valueOf(cfg.getAtReply().enabled()));
      at.put("selfId", Long.valueOf(cfg.getAtReply().selfId()));
      at.put("cooldownMs", Long.valueOf(cfg.getAtReply().cooldownMs()));
      at.put("queryCommand", cfg.getAtReply().queryCommand());
      at.put("template", cfg.getAtReply().template());
      data.put("atReply", at);
      LinkedHashMap<String, Object> binding = new LinkedHashMap<>();
      binding.put("enabled", Boolean.valueOf(cfg.getBinding().enabled()));
      binding.put("requestCommand", cfg.getBinding().requestCommand());
      binding.put("codeExpireMinutes", Integer.valueOf(cfg.getBinding().codeExpireMinutes()));
      binding.put("allowRebind", Boolean.valueOf(cfg.getBinding().allowRebind()));
      binding.put("requireBind", Boolean.valueOf(cfg.getBinding().requireBind()));
      data.put("binding", binding);
      LinkedHashMap<String, Object> signin = new LinkedHashMap<>();
      signin.put("enabled", Boolean.valueOf(cfg.getSignIn().enabled()));
      signin.put("command", cfg.getSignIn().command());
      signin.put("reward", Double.valueOf(cfg.getSignIn().reward()));
      data.put("signin", signin);
      LinkedHashMap<String, Object> relay = new LinkedHashMap<>();
      relay.put("groupToServer", Boolean.valueOf(cfg.getChatRelay().groupToServer()));
      relay.put("groupFormat", cfg.getChatRelay().groupFormat());
      data.put("chatRelay", relay);
      LinkedHashMap<String, Object> web = new LinkedHashMap<>();
      web.put("enabled", Boolean.valueOf(cfg.getWeb().enabled()));
      web.put("host", cfg.getWeb().host());
      web.put("port", Integer.valueOf(cfg.getWeb().port()));
      data.put("web", web);
      StringBuilder adminsSb = new StringBuilder();

      for (Long qq : cfg.getAdmins().qqNumbers()) {
         if (adminsSb.length() > 0) {
            adminsSb.append(",");
         }

         adminsSb.append(qq);
      }

      data.put("admins", adminsSb.toString());
      LinkedHashMap<String, Object> llm = new LinkedHashMap<>();
      llm.put("enabled", Boolean.valueOf(cfg.getLlm().enabled()));
      llm.put("apiUrl", cfg.getLlm().apiUrl());
      llm.put("apiKey", maskSecret(cfg.getLlm().apiKey()));
      llm.put("model", cfg.getLlm().model());
      llm.put("systemPrompt", cfg.getLlm().systemPrompt());
      llm.put("timeoutSeconds", Integer.valueOf(cfg.getLlm().timeoutSeconds()));
      llm.put("temperature", Double.valueOf(cfg.getLlm().temperature()));
      llm.put("contextTurns", Integer.valueOf(cfg.getLlm().contextTurns()));
      data.put("llm", llm);
      data.put("templates", cfg.getTemplates().all());
      data.put("bindingsData", this.plugin.bindingStore().bindingsForWeb());
      data.put("blacklist", this.plugin.bindingStore().listBlacklist());
      data.put("economyAvailable", Boolean.valueOf(this.plugin.economyAvailable()));
      data.put("coinsData", this.buildCoinsData());
      data.put("signinData", this.plugin.signInStore().list());
      sendJson(exchange, 200, Map.of("ok", true, "data", data));
   }

   private List<Map<String, Object>> buildCoinsData() {
      ArrayList<Map<String, Object>> result = new ArrayList<>();
      if (!this.plugin.economyAvailable()) {
         return result;
      } else {
         HashSet<UUID> seen = new HashSet<>();

         for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            this.addCoinRow(result, seen, p.getUniqueId(), p.getName());
         }

         for (BindingStore.Binding b : this.plugin.bindingStore().listBindings()) {
            this.addCoinRow(result, seen, b.playerUuid(), b.playerName());
         }

         return result;
      }
   }

   private void addCoinRow(List<Map<String, Object>> out, Set<UUID> seen, UUID uuid, String name) {
      if (uuid != null && seen.add(uuid)) {
         OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(uuid);
         LinkedHashMap<String, Object> m = new LinkedHashMap<>();
         m.put("uuid", uuid.toString());
         m.put("name", name == null ? offline.getName() : name);
         m.put("balance", Double.valueOf(this.plugin.vault().balance(offline)));
         out.add(m);
      }
   }

   private static void officialGroups(Map<String, Object> gm, List<Map<String, Object>> out) {
      out.add(gm);
   }

   private void handlePostConfig(HttpExchange exchange) throws IOException {
      String body = readBody(exchange);

      try {
         if (!(new ConfigWriter.MiniParser(body).parse() instanceof Map root)) {
            throw new IllegalArgumentException("提交数据必须是对象");
         }

         PluginConfig cfg = this.plugin.config();
         sanitizeToken(root, "official-bot", "secret", cfg.getOfficialBot().secret());
         sanitizeToken(root, "bot", "access-token", cfg.getBot().accessToken());
         sanitizeToken(root, "web", "access-token", cfg.getWeb().accessToken());
         sanitizeToken(root, "ws", "access-token", cfg.getWs().accessToken());
         sanitizeToken(root, "llm", "api-key", cfg.getLlm().apiKey());
         preserveBotDefaults(root, cfg);
         if (root.get("web") instanceof Map<?, ?> wm
            && (!wm.containsKey("access-token") || wm.get("access-token") == null || String.valueOf(wm.get("access-token")).isBlank())) {
            ((Map<?, String>)wm).put("access-token", cfg.getWeb().accessToken() == null ? "" : cfg.getWeb().accessToken());
         }

         if (root.get("admins") instanceof String adminsStr) {
            ArrayList<Long> adminList = new ArrayList<>();

            for (String part : adminsStr.split(",")) {
               String trimmed = part.trim();
               if (!trimmed.isEmpty()) {
                  try {
                     adminList.add(Long.parseLong(trimmed));
                  } catch (NumberFormatException var17) {
                  }
               }
            }

            root.put("admins", adminList);
         }

         LinkedHashMap<String, String> accountSec = new LinkedHashMap<>();
         accountSec.put("api-base", cfg.accountApiBase());
         root.put("account", accountSec);
         if (!root.containsKey("triggers")) {
            LinkedHashMap<String, Object> triggers = new LinkedHashMap<>();
            triggers.put("empty-on-zero-players", Boolean.valueOf(cfg.getTriggers().emptyOnZeroPlayers()));
            triggers.put("empty-only-on-transition", Boolean.valueOf(cfg.getTriggers().emptyOnlyOnTransition()));
            triggers.put("tps-warning-enabled", Boolean.valueOf(cfg.getTriggers().tpsWarningEnabled()));
            triggers.put("tps-warning-threshold", Double.valueOf(cfg.getTriggers().tpsWarningThreshold()));
            triggers.put("tps-warning-cooldown-seconds", Long.valueOf(cfg.getTriggers().tpsWarningCooldownSeconds()));
            root.put("triggers", triggers);
         }

         if (!root.containsKey("misc")) {
            LinkedHashMap<String, Object> misc = new LinkedHashMap<>();
            misc.put("async-shutdown-push", Boolean.valueOf(cfg.getMisc().asyncShutdownPush()));
            misc.put("shutdown-push-wait-ticks", Long.valueOf(cfg.getMisc().shutdownPushWaitTicks()));
            misc.put("start-push-delay-ticks", Long.valueOf(cfg.getMisc().startPushDelayTicks()));
            root.put("misc", misc);
         }

         Object officialGroupsObj;
         if ((officialGroupsObj = root.remove("officialGroups")) instanceof List) {
            List list = (List)officialGroupsObj;
            this.saveOfficialGroups(list);
         }

         String yaml = ConfigWriter.toYamlFromMap(root);
         Path cfgPath = this.plugin.getDataFolder().toPath().resolve("config.yml");
         Files.writeString(cfgPath, yaml, StandardCharsets.UTF_8);
      } catch (Exception var18) {
         this.plugin.pluginLogger().log(Level.WARNING, "[WebApi] 写回配置失败：" + var18.getMessage(), (Throwable)var18);
         sendJson(exchange, 400, Map.of("ok", false, "error", "写回配置失败：" + var18.getMessage()));
         return;
      }

      if (!this.plugin.reloadConfiguration()) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "配置格式有误，解析失败，请检查内容"));
      } else {
         sendJson(exchange, 200, Map.of("ok", true, "msg", "配置已保存，正在重载运行时"));
         Thread reloadThread = new Thread(() -> {
            try {
               this.plugin.restartRuntimeComponents();
            } catch (Throwable var2x) {
               this.plugin.pluginLogger().log(Level.WARNING, "[WebApi] 运行时重载异常：" + var2x.getMessage(), var2x);
            }
         }, "PaperQQBot-WebReload");
         reloadThread.setDaemon(true);
         reloadThread.start();
      }
   }

   private void saveOfficialGroups(List<?> list) {
      ArrayList<ChatGroup> groups = new ArrayList<>();

      for (Object o : list) {
         Map m;
         String openid;
         if (o instanceof Map && !(openid = asString((m = (Map)o).get("openid"), "")).isBlank()) {
            boolean enabled = Boolean.parseBoolean(asString(m.get("enabled"), "true"));
            LinkedHashSet<String> events = new LinkedHashSet<>();
            Object ev = m.get("events");
            if (ev instanceof String s) {
               for (String part : s.split(",")) {
                  if (!part.trim().isEmpty()) {
                     events.add(part.trim());
                  }
               }
            } else if (ev instanceof List) {
               for (Object p : (List)ev) {
                  if (!String.valueOf(p).isBlank()) {
                     events.add(String.valueOf(p));
                  }
               }
            }

            groups.add(new ChatGroup(openid, asString(m.get("note"), ""), enabled, events));
         }
      }

      this.plugin.officialGroups().replaceAll(groups);
   }

   private void handleTest(HttpExchange exchange) throws IOException {
      String body = readBody(exchange);
      String event = extractField(body, "event");
      if (event == null || event.isEmpty()) {
         event = "start";
      }

      if (!EVENT_NAMES.contains(event)) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "未知事件：" + event));
      } else {
         PlaceholderReplacer.EventContext ctx = !"join".equals(event) && !"quit".equals(event)
            ? (
               "chat".equals(event)
                  ? PlaceholderReplacer.forChat("WebUI", "WebUI", "WebUI 测试消息")
                  : ("death".equals(event) ? PlaceholderReplacer.forDeath("WebUI", "WebUI", "WebUI 被 PaperQQBot 测试击杀") : null)
            )
            : PlaceholderReplacer.forPlayer("WebUI", "WebUI");
         String template = this.plugin.config().getTemplates().get(event);
         if (template != null && !template.isBlank()) {
            String message = this.plugin.placeholder().replace(template, ctx);
            List<ChatGroup> targets = this.plugin.chatGroups().stream().filter(ChatGroup::enabled).toList();
            if (targets.isEmpty()) {
               sendJson(exchange, 400, Map.of("ok", false, "error", "无可用启用群"));
            } else {
               for (ChatGroup g : targets) {
                  this.plugin.messageSender().sendGroupMessage(g.key(), message);
               }

               sendJson(exchange, 200, Map.of("ok", true, "msg", "已向 " + targets.size() + " 个群推送事件 " + event));
            }
         } else {
            sendJson(exchange, 400, Map.of("ok", false, "error", "事件 " + event + " 未配置模板"));
         }
      }
   }

   private void handleReload(HttpExchange exchange) throws IOException {
      if (!this.plugin.reloadConfiguration()) {
         sendJson(exchange, 200, Map.of("ok", false, "msg", "配置解析失败，请检查 config.yml"));
      } else {
         sendJson(exchange, 200, Map.of("ok", true, "msg", "正在重载运行时"));
         Thread reloadThread = new Thread(() -> {
            try {
               this.plugin.restartRuntimeComponents();
            } catch (Throwable var2x) {
               this.plugin.pluginLogger().log(Level.WARNING, "[WebApi] 运行时重载异常：" + var2x.getMessage(), var2x);
            }
         }, "PaperQQBot-WebReload");
         reloadThread.setDaemon(true);
         reloadThread.start();
      }
   }

   private void handleManage(HttpExchange exchange) throws IOException {
      String body = readBody(exchange);
      String op = extractField(body, "op");
      if (op == null) {
         sendJson(exchange, 400, Map.of("ok", false, "error", "缺少 op"));
      } else {
         BindingStore bindingStore = this.plugin.bindingStore();
         switch (op) {
            case "unbind": {
               String playerx = extractField(body, "player");
               if (playerx == null || playerx.isBlank()) {
                  sendJson(exchange, 400, Map.of("ok", false, "error", "缺少 player"));
                  return;
               }

               boolean ok = bindingStore.adminUnbind(playerx);
               sendJson(exchange, 200, Map.of("ok", ok, "msg", ok ? "已解绑 " + playerx : "未找到该玩家的绑定"));
               break;
            }
            case "blacklist-add": {
               String entry = extractField(body, "entry");
               boolean ok = entry != null && !entry.isBlank() && bindingStore.addBlacklist(entry);
               sendJson(exchange, 200, Map.of("ok", ok, "msg", ok ? "已加入黑名单" : "条目为空或已存在"));
               break;
            }
            case "blacklist-remove": {
               String entry = extractField(body, "entry");
               boolean ok = entry != null && bindingStore.removeBlacklist(entry);
               sendJson(exchange, 200, Map.of("ok", ok, "msg", ok ? "已移出黑名单" : "条目不存在"));
               break;
            }
            case "coins-give":
            case "coins-take":
            case "coins-set":
               if (!this.plugin.economyAvailable()) {
                  sendJson(exchange, 200, Map.of("ok", false, "error", "服务器未安装经济插件，金币功能不可用"));
                  return;
               }

               String player = extractField(body, "player");
               String amountStr = extractField(body, "amount");
               UUID uuid = this.resolvePlayerUuid(player);
               if (uuid == null) {
                  sendJson(exchange, 400, Map.of("ok", false, "error", "玩家从未加入过服务器"));
                  return;
               }

               double amount;
               try {
                  amount = Double.parseDouble(amountStr);
               } catch (Exception var20) {
                  sendJson(exchange, 400, Map.of("ok", false, "error", "数量格式错误"));
                  return;
               }

               OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(uuid);

               boolean success = switch (op) {
                  case "coins-give" -> this.plugin.vault().deposit(offline, amount);
                  case "coins-take" -> this.plugin.vault().withdraw(offline, amount);
                  default -> {
                     double current = this.plugin.vault().balance(offline);
                     double delta = amount - current;
                     yield delta >= 0.0 ? this.plugin.vault().deposit(offline, delta) : this.plugin.vault().withdraw(offline, -delta);
                  }
               };
               if (!success) {
                  sendJson(exchange, 200, Map.of("ok", false, "error", "操作失败，请检查经济插件"));
                  return;
               }

               double balance = this.plugin.vault().balance(offline);
               sendJson(exchange, 200, Map.of("ok", true, "msg", "操作成功，余额 " + this.plugin.vault().format(balance)));
               break;
            default:
               sendJson(exchange, 400, Map.of("ok", false, "error", "未知 op：" + op));
         }
      }
   }

   private UUID resolvePlayerUuid(String name) {
      if (name == null) {
         return null;
      } else {
         Player online = this.plugin.getServer().getPlayerExact(name);
         if (online != null) {
            return online.getUniqueId();
         } else {
            for (BindingStore.Binding b : this.plugin.bindingStore().listBindings()) {
               if (b.playerName().equalsIgnoreCase(name)) {
                  return b.playerUuid();
               }
            }

            return null;
         }
      }
   }

   private static String readBody(HttpExchange exchange) throws IOException {
      byte[] buf = exchange.getRequestBody().readAllBytes();
      return new String(buf, StandardCharsets.UTF_8);
   }

   private static String str(Map<String, Object> map, String key) {
      Object v = map.get(key);
      return v == null ? "" : String.valueOf(v).trim();
   }

   private static String sendJson(HttpExchange exchange, int status, Object body) throws IOException {
      String json = MiniJson.toJson(body);
      byte[] data = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.sendResponseHeaders(status, (long)data.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(data);
      }

      return json;
   }

   private static String maskSecret(String t) {
      if (t != null && !t.isBlank()) {
         return t.length() < 8 ? "****" : t.substring(0, 4) + "****" + t.substring(t.length() - 4);
      } else {
         return "";
      }
   }

   private static double round2(double v) {
      return (double)Math.round(v * 100.0) / 100.0;
   }

   private static String extractField(String json, String key) {
      if (json == null) {
         return null;
      } else {
         int idx = json.indexOf("\"" + key + "\"");
         if (idx < 0) {
            return null;
         } else {
            int colon = json.indexOf(58, idx);
            if (colon < 0) {
               return null;
            } else {
               int start = json.indexOf(34, colon + 1);
               if (start < 0) {
                  return null;
               } else {
                  int end = json.indexOf(34, start + 1);
                  return end < 0 ? null : json.substring(start + 1, end);
               }
            }
         }
      }
   }

   private static void sanitizeToken(Map<String, Object> root, String section, String key, String currentValue) {
      if (root.get(section) instanceof Map sec) {
         if (sec.get(key) instanceof String s) {
            if (s.contains("****") || s.isBlank()) {
               sec.put(key, currentValue == null ? "" : currentValue);
            }
         }
      }
   }

   private static void preserveBotDefaults(Map<String, Object> root, PluginConfig cfg) {
      if (root.get("bot") instanceof Map bot) {
         bot.putIfAbsent("connect-timeout", cfg.getBot().connectTimeoutSec());
         bot.putIfAbsent("read-timeout", cfg.getBot().readTimeoutSec());
      }
   }

   private static String asString(Object o, String def) {
      return o == null ? def : String.valueOf(o);
   }
}
