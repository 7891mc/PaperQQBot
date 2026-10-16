package com.example.paperqqbot.core;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.GroupTarget;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;

public final class InMessageRouter {
   private static final int MAX_RECENT = 512;
   private final PaperQQBotPlugin plugin;
   private final Set<String> recentIds = Collections.synchronizedSet(new LinkedHashSet<>());

   public InMessageRouter(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
   }

   public void route(InboundGroupMessage msg) {
      if (msg.memberOpenid() != null) {
         this.plugin.officialGroups().learn(msg.groupKey(), msg.authorName());
      }

      if (msg.atBot()) {
         this.plugin.atReplyHandler().handleInteraction(msg);
      }

      this.maybeRelayToServer(msg);
   }

   private void maybeRelayToServer(InboundGroupMessage msg) {
      if (this.plugin.config().getChatRelay().groupToServer()) {
         String text = msg.text() == null ? "" : msg.text().trim();
         if (!text.startsWith("/")) {
            String content = msg.displayContent().trim();
            if (!content.isBlank()) {
               if (this.isGroupRelayEnabled(msg.groupKey())) {
                  if (this.markSeen(relayKey(msg))) {
                     String formatted = this.plugin.config().getChatRelay().format(msg.authorName(), content);
                     Bukkit.getScheduler().runTask(this.plugin, () -> {
                        try {
                           Bukkit.broadcastMessage(formatted);
                        } catch (Throwable var2x) {
                        }
                     });
                  }
               }
            }
         }
      }
   }

   private boolean isGroupRelayEnabled(String groupKey) {
      if (this.plugin.config().isOfficialMode()) {
         for (ChatGroup g : this.plugin.officialGroups().groups()) {
            if (g.key().equals(groupKey)) {
               return g.enabled();
            }
         }

         return true;
      } else {
         for (GroupTarget gx : this.plugin.config().getGroups()) {
            if (String.valueOf(gx.groupId()).equals(groupKey)) {
               return gx.enabled() && (gx.events().contains("*") || gx.events().contains("chat"));
            }
         }

         return false;
      }
   }

   private static String relayKey(InboundGroupMessage msg) {
      String id = msg.messageId();
      return id != null && !id.isBlank() ? id : msg.groupKey() + "|" + msg.identityKey() + "|" + msg.displayContent();
   }

   private boolean markSeen(String key) {
      Set<String> set = this.recentIds;
      synchronized (set) {
         if (!this.recentIds.add(key)) {
            return false;
         } else {
            while (this.recentIds.size() > 512) {
               Iterator<String> it = this.recentIds.iterator();
               it.next();
               it.remove();
            }

            return true;
         }
      }
   }
}
