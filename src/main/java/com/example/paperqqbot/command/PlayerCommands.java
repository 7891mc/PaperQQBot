package com.example.paperqqbot.command;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.BindingSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public final class PlayerCommands implements CommandExecutor {
   private final PaperQQBotPlugin plugin;

   public PlayerCommands(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   public void register() {
      this.bind("bindqq");
      this.bind("qqunbind");
      this.bind("coins");
   }

   private void bind(String name) {
      PluginCommand cmd = this.plugin.getCommand(name);
      if (cmd != null) {
         cmd.setExecutor(this);
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("该命令只能由玩家在游戏内执行。");
         return true;
      } else {
         String var7 = command.getName().toLowerCase();
         switch (var7) {
            case "bindqq":
               this.handleBindRequest(player);
               break;
            case "qqunbind":
               player.sendMessage(this.plugin.bindingStore().unbindByPlayer(player.getUniqueId()));
               break;
            case "coins":
               if (!this.plugin.economyAvailable()) {
                  player.sendMessage("服务器未安装经济插件，金币功能不可用。");
               } else {
                  double balance = this.plugin.vault().balance(player);
                  player.sendMessage("你的金币余额：" + this.plugin.vault().format(balance));
               }
               break;
            default:
               return false;
         }

         return true;
      }
   }

   private void handleBindRequest(Player player) {
      if (!this.plugin.config().getBinding().enabled()) {
         player.sendMessage("QQ 绑定功能当前未开启。");
      } else {
         BindingSettings bindCfg = this.plugin.config().getBinding();
         String result = this.plugin
            .bindingStore()
            .requestBindCodeByPlayer(player.getUniqueId(), player.getName(), bindCfg.allowRebind(), bindCfg.codeExpireMinutes());
         player.sendMessage(result);
         this.plugin.bindingGate().rescheduleKick(player.getUniqueId());
      }
   }
}
