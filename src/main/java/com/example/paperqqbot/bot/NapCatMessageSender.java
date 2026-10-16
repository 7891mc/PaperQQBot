package com.example.paperqqbot.bot;

import com.example.paperqqbot.core.MessageSender;
import java.util.concurrent.CompletableFuture;

public final class NapCatMessageSender implements MessageSender {
   private final NapCatClient client;

   public NapCatMessageSender(NapCatClient client) {
      this.client = client;
   }

   @Override
   public CompletableFuture<Boolean> sendGroupMessage(String groupKey, String text) {
      try {
         return this.client.sendGroupMessageAsync(Long.parseLong(groupKey.trim()), text);
      } catch (NumberFormatException var4) {
         return CompletableFuture.completedFuture(false);
      }
   }

   @Override
   public CompletableFuture<Boolean> replyToMessage(String groupKey, String text, String passiveMsgId) {
      return this.sendGroupMessage(groupKey, text);
   }
}
