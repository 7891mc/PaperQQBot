package com.example.paperqqbot;

import com.example.paperqqbot.account.AccountClient;
import com.example.paperqqbot.account.AccountStore;
import com.example.paperqqbot.account.ServerReporter;
import com.example.paperqqbot.bot.AtReplyHandler;
import com.example.paperqqbot.bot.LlmClient;
import com.example.paperqqbot.bot.NapCatClient;
import com.example.paperqqbot.bot.NapCatMessageSender;
import com.example.paperqqbot.bot.WsListener;
import com.example.paperqqbot.command.BotCommandExecutor;
import com.example.paperqqbot.command.PlayerCommands;
import com.example.paperqqbot.config.GroupTarget;
import com.example.paperqqbot.config.PluginConfig;
import com.example.paperqqbot.core.ChatGroup;
import com.example.paperqqbot.core.InMessageRouter;
import com.example.paperqqbot.core.MessageSender;
import com.example.paperqqbot.data.BindingStore;
import com.example.paperqqbot.data.OfficialGroupStore;
import com.example.paperqqbot.data.SignInStore;
import com.example.paperqqbot.economy.VaultEconomyHook;
import com.example.paperqqbot.event.ServerEventListener;
import com.example.paperqqbot.guard.BindingGate;
import com.example.paperqqbot.official.OfficialBotService;
import com.example.paperqqbot.placeholder.PlaceholderReplacer;
import com.example.paperqqbot.serverinfo.ServerInfoProvider;
import com.example.paperqqbot.web.WebServer;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperQQBotPlugin extends JavaPlugin {
   private volatile PluginConfig pluginConfig;
   private NapCatClient napCatClient;
   private NapCatMessageSender napCatSender;
   private WsListener wsListener;
   private OfficialBotService officialService;
   private ServerEventListener serverEventListener;
   private LlmClient llmClient;
   private WebServer webServer;
   private AtReplyHandler atReplyHandler;
   private PlaceholderReplacer placeholderReplacer;
   private ServerInfoProvider serverInfoProvider;
   private BindingStore bindingStore;
   private SignInStore signInStore;
   private OfficialGroupStore officialGroupStore;
   private InMessageRouter inMessageRouter;
   private volatile VaultEconomyHook vaultHook;
   private BindingGate bindingGate;
   private AccountStore accountStore;
   private AccountClient accountClient;
   private ServerReporter serverReporter;
   private PlayerCommands playerCommands;

   public void onEnable() {
      this.pluginConfig = this.readPluginConfig();
      this.serverInfoProvider = new ServerInfoProvider(this);
      this.placeholderReplacer = new PlaceholderReplacer(this);
      this.llmClient = new LlmClient(this);
      this.bindingStore = new BindingStore(this.getDataFolder());
      this.signInStore = new SignInStore(this.getDataFolder());
      this.officialGroupStore = new OfficialGroupStore(this.getDataFolder());
      this.setupEconomy();
      this.bindingGate = new BindingGate(this);
      this.getServer().getPluginManager().registerEvents(this.bindingGate, this);
      this.atReplyHandler = new AtReplyHandler(this);
      this.inMessageRouter = new InMessageRouter(this);
      this.startRuntime();
      this.serverEventListener = new ServerEventListener(this);
      this.getServer().getPluginManager().registerEvents(this.serverEventListener, this);
      this.playerCommands = new PlayerCommands(this);
      this.playerCommands.register();
      PluginCommand mainCommand = this.getCommand("paperqqbot");
      if (mainCommand != null) {
         mainCommand.setExecutor(new BotCommandExecutor(this));
      }

      this.accountStore = new AccountStore(this);
      this.accountClient = new AccountClient(this);
      this.serverReporter = new ServerReporter(this);
      this.serverReporter.reportStart();
      this.getServer().getScheduler().runTaskTimerAsynchronously(this, this::checkRemoteBind, 100L, 12000L);
      this.webServer = new WebServer(this);
      this.webServer.start();
      this.getLogger().info("PaperQQBot 已启用，当前模式：" + this.pluginConfig.getMode());
   }

   public void onDisable() {
      this.pushShutdownMessage();
      if (this.serverReporter != null) {
         this.serverReporter.reportStop();
      }

      this.stopRuntime();
      if (this.bindingGate != null) {
         this.bindingGate.shutdown();
      }

      if (this.webServer != null) {
         this.webServer.stop();
      }

      if (this.atReplyHandler != null) {
         this.atReplyHandler.shutdown();
      }

      if (this.serverReporter != null) {
         this.serverReporter.shutdown();
      }

      this.getLogger().info("PaperQQBot 已卸载");
   }

   private void pushShutdownMessage() {
      try {
         ServerEventListener listener = this.serverEventListener;
         PluginConfig cfg = this.pluginConfig;
         if (listener == null || cfg == null || !cfg.isEventEnabled("stop")) {
            return;
         }

         long waitMs = Math.max(1L, cfg.getMisc().shutdownPushWaitTicks()) * 50L;
         if (cfg.getMisc().asyncShutdownPush()) {
            Thread t = new Thread(() -> listener.pushStopSync(waitMs), "PaperQQBot-ShutdownPush");
            t.setDaemon(true);
            t.start();

            try {
               t.join(waitMs + 2000L);
            } catch (InterruptedException var7) {
               Thread.currentThread().interrupt();
            }
         } else {
            listener.pushStopSync(waitMs);
         }
      } catch (Throwable var8) {
         this.getLogger().warning("[关服推送] 异常：" + var8.getMessage());
      }
   }

   private void startRuntime() {
      if (this.pluginConfig.isOfficialMode()) {
         this.officialService = new OfficialBotService(this);
         this.officialService.start();
      } else {
         this.napCatClient = new NapCatClient(this);
         this.napCatSender = new NapCatMessageSender(this.napCatClient);
         this.wsListener = new WsListener(this, this.atReplyHandler);
         if (this.pluginConfig.getWs().enabled()) {
            this.wsListener.start();
         } else {
            this.getLogger().info("NapCat WS 监听未启用（ws.enabled=false），仅使用 HTTP 推送。");
         }
      }
   }

   private void stopRuntime() {
      if (this.officialService != null) {
         this.officialService.shutdown();
         this.officialService = null;
      }

      if (this.wsListener != null) {
         this.wsListener.stop();
         this.wsListener = null;
      }

      if (this.napCatClient != null) {
         this.napCatClient.shutdown();
         this.napCatClient = null;
         this.napCatSender = null;
      }
   }

   public void restartRuntimeComponents() {
      this.stopRuntime();
      this.pluginConfig = this.readPluginConfig();
      this.setupEconomy();
      if (this.webServer != null && this.webServer.needsRestart(this.pluginConfig.getWeb())) {
         this.webServer.stop();
         this.webServer = null;
      }

      this.startRuntime();
      if (this.webServer == null) {
         this.webServer = new WebServer(this);
         this.webServer.start();
      }
   }

   public void setupEconomy() {
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         this.vaultHook = null;
      } else {
         try {
            this.vaultHook = VaultEconomyHook.create(this);
         } catch (Throwable var2) {
            this.getLogger().warning("[Economy] Vault 经济服务初始化失败：" + var2.getMessage());
            this.vaultHook = null;
         }

         if (this.vaultHook == null) {
            this.getLogger().info("[Economy] 未检测到经济插件，签到金币功能不可用。");
         }
      }
   }

   public boolean economyAvailable() {
      return this.vaultHook != null && this.vaultHook.isAvailable();
   }

   private PluginConfig readPluginConfig() {
      File configFile = new File(this.getDataFolder(), "config.yml");
      if (!configFile.exists()) {
         this.saveResource("config.yml", false);
      }

      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
      return PluginConfig.load(yaml);
   }

   public boolean reloadConfiguration() {
      try {
         File configFile = new File(this.getDataFolder(), "config.yml");
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
         this.pluginConfig = PluginConfig.load(yaml);
         return true;
      } catch (Throwable var3) {
         this.getLogger().warning("配置解析失败：" + var3.getMessage());
         return false;
      }
   }

   public boolean runtimeConnected() {
      return this.pluginConfig.isOfficialMode()
         ? this.officialService != null && this.officialService.isConnected()
         : this.wsListener != null && this.wsListener.isConnected();
   }

   public Logger pluginLogger() {
      return this.getLogger();
   }

   public MessageSender messageSender() {
      return (MessageSender)(this.pluginConfig.isOfficialMode() ? this.officialService : this.napCatSender);
   }

   public List<ChatGroup> chatGroups() {
      if (this.pluginConfig.isOfficialMode()) {
         return this.officialGroupStore.groups();
      } else {
         ArrayList<ChatGroup> result = new ArrayList<>();

         for (GroupTarget g : this.pluginConfig.getGroups()) {
            result.add(new ChatGroup(String.valueOf(g.groupId()), g.name(), g.enabled(), new LinkedHashSet<>(g.events())));
         }

         return result;
      }
   }

   public PluginConfig config() {
      return this.pluginConfig;
   }

   public NapCatClient napCat() {
      return this.napCatClient;
   }

   public LlmClient llm() {
      return this.llmClient;
   }

   public AtReplyHandler atReplyHandler() {
      return this.atReplyHandler;
   }

   public PlaceholderReplacer placeholder() {
      return this.placeholderReplacer;
   }

   public ServerInfoProvider serverInfo() {
      return this.serverInfoProvider;
   }

   public BindingStore bindingStore() {
      return this.bindingStore;
   }

   public BindingGate bindingGate() {
      return this.bindingGate;
   }

   public SignInStore signInStore() {
      return this.signInStore;
   }

   public VaultEconomyHook vault() {
      return this.vaultHook;
   }

   public OfficialGroupStore officialGroups() {
      return this.officialGroupStore;
   }

   public InMessageRouter inMessageRouter() {
      return this.inMessageRouter;
   }

   public OfficialBotService officialBot() {
      return this.officialService;
   }

   public AccountStore accountStore() {
      return this.accountStore;
   }

   public AccountClient accountClient() {
      return this.accountClient;
   }

   private void checkRemoteBind() {
      if (this.accountStore != null && this.accountClient != null && this.accountStore.isBound()) {
         try {
            AccountClient.Result r = this.accountClient.validate();
            if (!r.ok) {
               this.getLogger().warning("[Account] 绑定状态校验失败：" + r.error);
               return;
            }

            Object bound = r.outer.get("bound");
            Boolean b;
            if (bound instanceof Boolean && !b = (Boolean)bound) {
               this.getLogger().info("[Account] 官网已解绑本实例，WebUI 回到待绑定状态");
               this.accountStore.clearBound();
            }
         } catch (Throwable var4) {
            this.getLogger().warning("[Account] 绑定状态校验异常：" + var4.getMessage());
         }
      }
   }
}
