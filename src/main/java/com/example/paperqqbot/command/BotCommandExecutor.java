package com.example.paperqqbot.command;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.core.ChatGroup;
import com.example.paperqqbot.data.BindingStore;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BotCommandExecutor implements CommandExecutor, TabCompleter {
   private final PaperQQBotPlugin plugin;

   public BotCommandExecutor(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("paperqqbot.admin")) {
         sender.sendMessage("§c你没有权限执行该命令。");
         return true;
      } else if (args.length == 0) {
         this.sendHelp(sender);
         return true;
      } else {
         String var6 = args[0].toLowerCase();
         switch (var6) {
            case "reload":
               this.plugin.restartRuntimeComponents();
               sender.sendMessage("§aPaperQQBot 已重载并按当前模式重启机器人。");
               break;
            case "status":
               this.sendStatus(sender);
               break;
            case "test":
               if (args.length < 2) {
                  sender.sendMessage("§c用法：/paperqqbot test <消息>");
                  return true;
               }

               String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
               List<ChatGroup> groups = this.plugin.chatGroups();
               if (groups.isEmpty()) {
                  sender.sendMessage("§c当前没有可用的群。");
                  return true;
               }

               for (ChatGroup g : groups) {
                  this.plugin.messageSender().sendGroupMessage(g.key(), msg);
               }

               sender.sendMessage("§a测试消息已发送到 " + groups.size() + " 个群。");
               break;
            case "bindlist":
               List<BindingStore.Binding> list = this.plugin.bindingStore().listBindings();
               if (list.isEmpty()) {
                  sender.sendMessage("§7当前没有任何绑定。");
                  return true;
               }

               sender.sendMessage("§6绑定列表（" + list.size() + "）：");

               for (BindingStore.Binding b : list) {
                  sender.sendMessage("§e" + b.playerName() + " §7<- 已绑定");
               }
               break;
            case "unbind":
               if (args.length < 2) {
                  sender.sendMessage("§c用法：/paperqqbot unbind <玩家名>");
                  return true;
               }

               boolean ok = this.plugin.bindingStore().adminUnbind(args[1]);
               sender.sendMessage(ok ? "§a已解绑玩家 " + args[1] : "§c未找到玩家 " + args[1] + " 的绑定。");
               break;
            case "blacklist":
               this.handleBlacklist(sender, args);
               break;
            case "coins":
               this.handleCoins(sender, args);
               break;
            case "help":
               this.sendHelp(sender);
               break;
            default:
               this.sendHelp(sender);
         }

         return true;
      }
   }

   private void handleBlacklist(CommandSender sender, String[] args) {
      BindingStore store = this.plugin.bindingStore();
      if (args.length < 2) {
         sender.sendMessage("§c用法：/paperqqbot blacklist <add|remove|list> [条目]");
      } else {
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "add":
               if (args.length < 3) {
                  sender.sendMessage("§c用法：/paperqqbot blacklist add <玩家名/OpenID>");
                  return;
               }

               String entry = args[2];
               boolean ok = store.addBlacklist(entry);
               sender.sendMessage(ok ? "§a已加入黑名单：" + entry : "§e该条目已在黑名单中。");
               break;
            case "remove":
            case "del":
            case "delete":
               if (args.length < 3) {
                  sender.sendMessage("§c用法：/paperqqbot blacklist remove <玩家名/OpenID>");
                  return;
               }

               String entry = args[2];
               boolean ok = store.removeBlacklist(entry);
               sender.sendMessage(ok ? "§a已移出黑名单：" + entry : "§c黑名单中不存在该条目。");
               break;
            case "list":
               List<String> list = store.listBlacklist();
               if (list.isEmpty()) {
                  sender.sendMessage("§7黑名单为空。");
                  return;
               }

               sender.sendMessage("§6黑名单（" + list.size() + "）：");

               for (String e : list) {
                  sender.sendMessage("§c- " + e);
               }
               break;
            default:
               sender.sendMessage("§c用法：/paperqqbot blacklist <add|remove|list> [条目]");
         }
      }
   }

   private void handleCoins(CommandSender sender, String[] args) {
      if (!this.plugin.economyAvailable()) {
         sender.sendMessage("§c服务器未安装经济插件，金币功能不可用。");
      } else if (args.length < 2) {
         sender.sendMessage("§c用法：/paperqqbot coins <give|take|set|look> ...");
      } else {
         String var3 = args[1].toLowerCase();
         switch (var3) {
            case "look":
               if (args.length < 3) {
                  sender.sendMessage("§c用法：/paperqqbot coins look <玩家名>");
                  return;
               }

               BotCommandExecutor.PlayerRef refx = this.resolvePlayer(args[2]);
               if (refx == null) {
                  sender.sendMessage("§c玩家 " + args[2] + " 从未加入过服务器。");
                  return;
               }

               OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(refx.uuid());
               sender.sendMessage("§e" + args[2] + " 的金币余额：" + this.plugin.vault().format(this.plugin.vault().balance(offline)));
               break;
            case "give":
            case "take":
            case "set":
               if (args.length < 4) {
                  sender.sendMessage("§c用法：/paperqqbot coins " + args[1] + " <玩家名> <数量>");
                  return;
               }

               BotCommandExecutor.PlayerRef ref = this.resolvePlayer(args[2]);
               if (ref == null) {
                  sender.sendMessage("§c玩家 " + args[2] + " 从未加入过服务器。");
                  return;
               }

               double amount;
               try {
                  amount = Double.parseDouble(args[3]);
               } catch (NumberFormatException var17) {
                  sender.sendMessage("§c数量格式错误。");
                  return;
               }

               if (amount < 0.0) {
                  sender.sendMessage("§c数量不能为负。");
                  return;
               }

               OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(ref.uuid());
               String var10 = args[1].toLowerCase();

               if (!(switch (var10) {
                  case "give" -> this.plugin.vault().deposit(offline, amount);
                  case "take" -> this.plugin.vault().withdraw(offline, amount);
                  default -> {
                     double current = this.plugin.vault().balance(offline);
                     double delta = amount - current;
                     yield delta >= 0.0 ? this.plugin.vault().deposit(offline, delta) : this.plugin.vault().withdraw(offline, -delta);
                  }
               })) {
                  sender.sendMessage("§c操作失败，请检查经济插件。");
                  return;
               }

               sender.sendMessage("§a操作完成，当前余额：" + this.plugin.vault().format(this.plugin.vault().balance(offline)));
               break;
            default:
               sender.sendMessage("§c用法：/paperqqbot coins <give|take|set|look> ...");
         }
      }
   }

   private BotCommandExecutor.PlayerRef resolvePlayer(String name) {
      Player online = this.plugin.getServer().getPlayerExact(name);
      if (online != null) {
         return new BotCommandExecutor.PlayerRef(online.getUniqueId());
      } else {
         for (BindingStore.Binding b : this.plugin.bindingStore().listBindings()) {
            if (b.playerName().equalsIgnoreCase(name)) {
               return new BotCommandExecutor.PlayerRef(b.playerUuid());
            }
         }

         return null;
      }
   }

   private void sendStatus(CommandSender sender) {
      boolean official = this.plugin.config().isOfficialMode();
      sender.sendMessage("§6===== PaperQQBot 状态 =====");
      sender.sendMessage("§7机器人模式：§e" + this.plugin.config().getMode());
      if (official) {
         boolean connected = this.plugin.officialBot() != null && this.plugin.officialBot().isConnected();
         sender.sendMessage("§7官方机器人：" + (connected ? "§a已连接" : "§c未连接"));
         sender.sendMessage("§7AppID：§e" + this.plugin.config().getOfficialBot().appId());
      } else {
         sender.sendMessage("§7NapCat HTTP：" + (this.plugin.napCat() != null ? "§a已就绪" : "§c未就绪"));
         sender.sendMessage("§7WS 监听：" + (this.plugin.napCat() != null && this.plugin.config().getWs().enabled() ? "§a启用" : "§c未启用"));
      }

      sender.sendMessage("§7群数量：§e" + this.plugin.chatGroups().size());
      sender.sendMessage("§7绑定数量：§e" + this.plugin.bindingStore().listBindings().size());
      sender.sendMessage("§7黑名单：§e" + this.plugin.bindingStore().listBlacklist().size());
   }

   private void sendHelp(CommandSender sender) {
      sender.sendMessage("§6===== PaperQQBot 命令 =====");
      sender.sendMessage("§e/paperqqbot reload §7- 重载配置并重启机器人");
      sender.sendMessage("§e/paperqqbot status §7- 查看状态");
      sender.sendMessage("§e/paperqqbot test <消息> §7- 发送测试消息");
      sender.sendMessage("§e/paperqqbot bindlist §7- 查看绑定列表");
      sender.sendMessage("§e/paperqqbot unbind <玩家名> §7- 强制解绑");
      sender.sendMessage("§e/paperqqbot blacklist <add|remove|list> §7- 黑名单管理");
      sender.sendMessage("§e/paperqqbot coins <give|take|set|look> §7- 金币管理");
      sender.sendMessage("§e/qq绑定 <验证码> §7- 玩家绑定 QQ");
      sender.sendMessage("§e/qq解绑 §7- 玩家解除绑定");
      sender.sendMessage("§e/金币 §7- 查询金币余额");
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("paperqqbot.admin")) {
         return List.of();
      } else if (args.length == 1) {
         return filterPrefix(args[0], List.of("reload", "status", "test", "bindlist", "unbind", "blacklist", "coins", "help"));
      } else if (args.length == 2 && args[0].equalsIgnoreCase("blacklist")) {
         return filterPrefix(args[1], List.of("add", "remove", "list"));
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("coins") ? filterPrefix(args[1], List.of("give", "take", "set", "look")) : List.of();
      }
   }

   private static List<String> filterPrefix(String prefix, List<String> options) {
      String p = prefix.toLowerCase();
      return options.stream().filter(o -> o.startsWith(p)).toList();
   }

   private static record PlayerRef(UUID uuid) {
   }
}
