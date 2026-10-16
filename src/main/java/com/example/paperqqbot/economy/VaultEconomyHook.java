package com.example.paperqqbot.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyHook {
   private final Economy economy;

   private VaultEconomyHook(Economy economy) {
      this.economy = economy;
   }

   public static VaultEconomyHook create(JavaPlugin plugin) {
      RegisteredServiceProvider rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
      return rsp != null && rsp.getProvider() != null ? new VaultEconomyHook((Economy)rsp.getProvider()) : null;
   }

   public boolean isAvailable() {
      return this.economy != null;
   }

   public boolean deposit(OfflinePlayer player, double amount) {
      return this.apply(player, amount, true);
   }

   public boolean withdraw(OfflinePlayer player, double amount) {
      return this.apply(player, amount, false);
   }

   private boolean apply(OfflinePlayer player, double amount, boolean deposit) {
      if (this.economy != null && player != null && !(amount <= 0.0)) {
         try {
            EconomyResponse resp = deposit ? this.economy.depositPlayer(player, amount) : this.economy.withdrawPlayer(player, amount);
            return resp != null && resp.transactionSuccess();
         } catch (Throwable var6) {
            return false;
         }
      } else {
         return false;
      }
   }

   public double balance(OfflinePlayer player) {
      if (this.economy != null && player != null) {
         try {
            return this.economy.getBalance(player);
         } catch (Throwable var3) {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   public String format(double amount) {
      try {
         return this.economy.format(amount);
      } catch (Throwable var4) {
         return amount == Math.rint(amount) ? String.valueOf((long)amount) : String.valueOf(amount);
      }
   }

   public String currencyName() {
      try {
         String name = this.economy.currencyNamePlural();
         return name == null ? "" : name;
      } catch (Throwable var2) {
         return "";
      }
   }
}
