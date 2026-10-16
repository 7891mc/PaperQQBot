package com.example.paperqqbot.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class PluginConfig {
   private final BotSettings bot;
   private final ServerInfo server;
   private final List<GroupTarget> groups;
   private final EventToggles eventToggles;
   private final Triggers triggers;
   private final Templates templates;
   private final Misc misc;
   private final WsSettings ws;
   private final AtReplySettings atReply;
   private final WebSettings web;
   private final Admins admins;
   private final LlmSettings llm;
   private final String mode;
   private final OfficialBotSettings officialBot;
   private final BindingSettings binding;
   private final SignInSettings signin;
   private final ChatRelaySettings chatRelay;
   private final String accountApiBase;

   private PluginConfig(
      BotSettings bot,
      ServerInfo server,
      List<GroupTarget> groups,
      EventToggles eventToggles,
      Triggers triggers,
      Templates templates,
      Misc misc,
      WsSettings ws,
      AtReplySettings atReply,
      WebSettings web,
      Admins admins,
      LlmSettings llm,
      String mode,
      OfficialBotSettings officialBot,
      BindingSettings binding,
      SignInSettings signin,
      ChatRelaySettings chatRelay,
      String accountApiBase
   ) {
      this.bot = bot;
      this.server = server;
      this.groups = groups;
      this.eventToggles = eventToggles;
      this.triggers = triggers;
      this.templates = templates;
      this.misc = misc;
      this.ws = ws;
      this.atReply = atReply;
      this.web = web;
      this.admins = admins;
      this.llm = llm;
      this.mode = mode;
      this.officialBot = officialBot;
      this.binding = binding;
      this.signin = signin;
      this.chatRelay = chatRelay;
      this.accountApiBase = accountApiBase;
   }

   public static PluginConfig load(ConfigurationSection root) {
      ConfigurationSection botSec = root.getConfigurationSection("bot");
      BotSettings bot = loadBot(botSec);
      ConfigurationSection serverSec = root.getConfigurationSection("server");
      ServerInfo server = loadServer(serverSec);
      List rawGroups = root.getList("groups");
      List<GroupTarget> groups = loadGroups(rawGroups);
      ConfigurationSection toggleSec = root.getConfigurationSection("event-toggles");
      EventToggles toggles = loadToggles(toggleSec);
      ConfigurationSection triggerSec = root.getConfigurationSection("triggers");
      Triggers triggers = loadTriggers(triggerSec);
      ConfigurationSection tplSec = root.getConfigurationSection("templates");
      Templates templates = loadTemplates(tplSec);
      ConfigurationSection miscSec = root.getConfigurationSection("misc");
      Misc misc = loadMisc(miscSec);
      ConfigurationSection wsSec = root.getConfigurationSection("ws");
      WsSettings ws = loadWs(wsSec);
      ConfigurationSection atSec = root.getConfigurationSection("at-reply");
      AtReplySettings atReply = loadAtReply(atSec);
      ConfigurationSection webSec = root.getConfigurationSection("web");
      WebSettings web = loadWeb(webSec);
      List rawAdmins = root.getList("admins");
      Admins admins = loadAdmins(rawAdmins);
      ConfigurationSection llmSec = root.getConfigurationSection("llm");
      LlmSettings llm = loadLlm(llmSec);
      String mode = root.getString("mode", "napcat");
      if (mode == null || mode.isBlank()) {
         mode = "napcat";
      }

      mode = mode.trim().toLowerCase();
      ConfigurationSection officialSec = root.getConfigurationSection("official-bot");
      OfficialBotSettings officialBot = officialSec == null
         ? OfficialBotSettings.fallback()
         : new OfficialBotSettings(officialSec.getString("app-id", ""), officialSec.getString("secret", ""));
      ConfigurationSection bindSec = root.getConfigurationSection("binding");
      BindingSettings binding = loadBinding(bindSec);
      ConfigurationSection signinSec = root.getConfigurationSection("signin");
      SignInSettings signin = loadSignIn(signinSec);
      ConfigurationSection relaySec = root.getConfigurationSection("chat-relay");
      ChatRelaySettings chatRelay = loadChatRelay(relaySec);
      String accountApiBase = root.getString("account.api-base", "https://dlapi.0p.ink/api.php");
      if (accountApiBase == null || accountApiBase.isBlank()) {
         accountApiBase = "https://dlapi.0p.ink/api.php";
      }

      return new PluginConfig(
         bot, server, groups, toggles, triggers, templates, misc, ws, atReply, web, admins, llm, mode, officialBot, binding, signin, chatRelay, accountApiBase
      );
   }

   private static BindingSettings loadBinding(ConfigurationSection sec) {
      if (sec == null) {
         return BindingSettings.fallback();
      } else {
         BindingSettings fb = BindingSettings.fallback();
         return new BindingSettings(
            sec.getBoolean("enabled", fb.enabled()),
            sec.getString("request-command", fb.requestCommand()),
            sec.getInt("code-expire-minutes", fb.codeExpireMinutes()),
            sec.getBoolean("allow-rebind", fb.allowRebind()),
            sec.getBoolean("require-bind", fb.requireBind())
         );
      }
   }

   private static SignInSettings loadSignIn(ConfigurationSection sec) {
      if (sec == null) {
         return SignInSettings.fallback();
      } else {
         SignInSettings fb = SignInSettings.fallback();
         return new SignInSettings(sec.getBoolean("enabled", fb.enabled()), sec.getString("command", fb.command()), sec.getDouble("reward", fb.reward()));
      }
   }

   private static ChatRelaySettings loadChatRelay(ConfigurationSection sec) {
      if (sec == null) {
         return ChatRelaySettings.fallback();
      } else {
         ChatRelaySettings fb = ChatRelaySettings.fallback();
         return new ChatRelaySettings(sec.getBoolean("group-to-server", fb.groupToServer()), sec.getString("group-format", fb.groupFormat()));
      }
   }

   private static Admins loadAdmins(List<?> raw) {
      LinkedHashSet<Long> set = new LinkedHashSet<>();
      if (raw != null) {
         for (Object o : raw) {
            if (o != null) {
               try {
                  set.add(Long.valueOf(Long.parseLong(String.valueOf(o).trim())));
               } catch (NumberFormatException var5) {
               }
            }
         }
      }

      return new Admins(set);
   }

   private static LlmSettings loadLlm(ConfigurationSection sec) {
      if (sec == null) {
         return LlmSettings.fallback();
      } else {
         LlmSettings fb = LlmSettings.fallback();
         return new LlmSettings(
            sec.getBoolean("enabled", fb.enabled()),
            sec.getString("api-url", fb.apiUrl()),
            sec.getString("api-key", fb.apiKey()),
            sec.getString("model", fb.model()),
            sec.getString("system-prompt", fb.systemPrompt()),
            sec.getInt("timeout-seconds", fb.timeoutSeconds()),
            sec.getDouble("temperature", fb.temperature()),
            Math.max(0, Math.min(20, sec.getInt("context-turns", fb.contextTurns())))
         );
      }
   }

   private static WebSettings loadWeb(ConfigurationSection sec) {
      return sec == null
         ? WebSettings.fallback()
         : new WebSettings(sec.getBoolean("enabled", true), sec.getString("host", "127.0.0.1"), sec.getInt("port", 8765), sec.getString("access-token", ""));
   }

   private static WsSettings loadWs(ConfigurationSection sec) {
      if (sec == null) {
         return WsSettings.fallback();
      } else {
         String token = sec.getString("access-token", "");
         return new WsSettings(sec.getBoolean("enabled", false), sec.getString("url", ""), token, sec.getLong("reconnect-ms", 5000L));
      }
   }

   private static AtReplySettings loadAtReply(ConfigurationSection sec) {
      return sec == null
         ? AtReplySettings.fallback()
         : new AtReplySettings(
            sec.getBoolean("enabled", false),
            sec.getLong("self-id", 0L),
            sec.getString("template", AtReplySettings.fallback().template()),
            sec.getLong("cooldown-ms", 3000L),
            sec.getString("query-command", AtReplySettings.fallback().queryCommand())
         );
   }

   private static BotSettings loadBot(ConfigurationSection sec) {
      return sec == null
         ? new BotSettings("http://127.0.0.1:3000", "", 10, 15, false, 2, 1500L)
         : new BotSettings(
            sec.getString("http-url", "http://127.0.0.1:3000"),
            sec.getString("access-token", ""),
            sec.getInt("connect-timeout", 10),
            sec.getInt("read-timeout", 15),
            sec.getBoolean("debug", false),
            sec.getInt("retry", 2),
            sec.getLong("retry-interval-millis", 1500L)
         );
   }

   private static ServerInfo loadServer(ConfigurationSection sec) {
      return sec == null
         ? ServerInfo.fallback()
         : new ServerInfo(
            sec.getString("name", "未配置"),
            sec.getString("address", "unknown"),
            sec.getString("contact", ""),
            sec.getString("extra", ""),
            sec.getString("website", "")
         );
   }

   private static List<GroupTarget> loadGroups(List<?> raw) {
      ArrayList<GroupTarget> list = new ArrayList<>();
      if (raw == null) {
         return list;
      } else {
         Iterator var2 = raw.iterator();

         while (true) {
            long groupId;
            Map map;
            while (true) {
               if (!var2.hasNext()) {
                  return list;
               }

               Object item = var2.next();
               Map rawMap;
               Object gid;
               if (item instanceof Map && (gid = (map = rawMap = (Map)item).get("group-id")) != null) {
                  try {
                     groupId = Long.parseLong(String.valueOf(gid).trim());
                     break;
                  } catch (NumberFormatException var16) {
                  }
               }
            }

            String name = String.valueOf(map.getOrDefault("name", ""));
            boolean enabled = toBool(map.get("enabled"), true);
            Object evRaw = map.get("events");
            HashSet<String> events = new HashSet<>();
            if (evRaw instanceof List) {
               for (Object o : (List)evRaw) {
                  if (o != null) {
                     events.add(String.valueOf(o));
                  }
               }
            }

            list.add(new GroupTarget(groupId, name, enabled, events));
         }
      }
   }

   private static boolean toBool(Object o, boolean def) {
      if (o instanceof Boolean b) {
         return b;
      } else {
         return o == null ? def : Boolean.parseBoolean(String.valueOf(o));
      }
   }

   private static EventToggles loadToggles(ConfigurationSection sec) {
      if (sec == null) {
         return new EventToggles(Map.of());
      } else {
         HashMap<String, Boolean> map = new HashMap<>();

         for (String key : sec.getKeys(false)) {
            map.put(key, sec.getBoolean(key));
         }

         return new EventToggles(map);
      }
   }

   private static Triggers loadTriggers(ConfigurationSection sec) {
      return sec == null
         ? Triggers.fallback()
         : new Triggers(
            sec.getBoolean("empty-on-zero-players", true),
            sec.getBoolean("empty-only-on-transition", true),
            sec.getBoolean("tps-warning-enabled", true),
            sec.getDouble("tps-warning-threshold", 15.0),
            sec.getLong("tps-warning-cooldown-seconds", 300L)
         );
   }

   private static Templates loadTemplates(ConfigurationSection sec) {
      if (sec == null) {
         return new Templates(Map.of());
      } else {
         HashMap<String, String> map = new HashMap<>();

         for (String key : sec.getKeys(false)) {
            map.put(key, sec.getString(key, ""));
         }

         return new Templates(map);
      }
   }

   private static Misc loadMisc(ConfigurationSection sec) {
      return sec == null
         ? Misc.fallback()
         : new Misc(sec.getBoolean("async-shutdown-push", true), sec.getLong("shutdown-push-wait-ticks", 60L), sec.getLong("start-push-delay-ticks", 40L));
   }

   public BotSettings getBot() {
      return this.bot;
   }

   public ServerInfo getServer() {
      return this.server;
   }

   public List<GroupTarget> getGroups() {
      return this.groups;
   }

   public EventToggles getEventToggles() {
      return this.eventToggles;
   }

   public Triggers getTriggers() {
      return this.triggers;
   }

   public Templates getTemplates() {
      return this.templates;
   }

   public Misc getMisc() {
      return this.misc;
   }

   public WsSettings getWs() {
      return this.ws;
   }

   public AtReplySettings getAtReply() {
      return this.atReply;
   }

   public WebSettings getWeb() {
      return this.web;
   }

   public Admins getAdmins() {
      return this.admins;
   }

   public LlmSettings getLlm() {
      return this.llm;
   }

   public String getMode() {
      return this.mode;
   }

   public boolean isOfficialMode() {
      return "official".equals(this.mode);
   }

   public OfficialBotSettings getOfficialBot() {
      return this.officialBot;
   }

   public BindingSettings getBinding() {
      return this.binding;
   }

   public SignInSettings getSignIn() {
      return this.signin;
   }

   public ChatRelaySettings getChatRelay() {
      return this.chatRelay;
   }

   public String accountApiBase() {
      return this.accountApiBase;
   }

   public int countEnabledGroups() {
      int n = 0;

      for (GroupTarget g : this.groups) {
         if (g.enabled()) {
            n++;
         }
      }

      return n;
   }

   public boolean isEventEnabled(String eventName) {
      return this.eventToggles.isEnabled(eventName);
   }
}
