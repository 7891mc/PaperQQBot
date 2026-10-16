package com.example.paperqqbot.core;

import java.util.concurrent.CompletableFuture;

public interface MessageSender {
   CompletableFuture<Boolean> sendGroupMessage(String var1, String var2);

   CompletableFuture<Boolean> replyToMessage(String var1, String var2, String var3);
}
