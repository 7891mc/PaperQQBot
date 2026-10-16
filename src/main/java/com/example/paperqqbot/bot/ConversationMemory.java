package com.example.paperqqbot.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationMemory {
   private static final int MAX_SESSIONS = 512;
   private final Map<String, Deque<ConversationMemory.Turn>> sessions = new ConcurrentHashMap<>();

   public List<ConversationMemory.Turn> history(String key, int maxTurns) {
      Deque<ConversationMemory.Turn> q = this.sessions.get(key);
      if (q != null && maxTurns > 0) {
         synchronized (q) {
            int n = Math.min(maxTurns * 2, q.size());
            if (n <= 0) {
               return List.of();
            } else {
               ArrayList<ConversationMemory.Turn> all = new ArrayList<>(q);
               return new ArrayList<>(all.subList(all.size() - n, all.size()));
            }
         }
      } else {
         return List.of();
      }
   }

   public void appendExchange(String key, String user, String assistant, int maxTurns) {
      if (user != null && !user.isBlank() && assistant != null && !assistant.isBlank()) {
         Deque q;
         Deque deque = q = this.sessions.computeIfAbsent(key, k -> new ArrayDeque<>());
         synchronized (deque) {
            q.addLast(new ConversationMemory.Turn("user", user));
            q.addLast(new ConversationMemory.Turn("assistant", assistant));
            int max = Math.max(1, maxTurns) * 2;

            while (q.size() > max) {
               q.pollFirst();
            }
         }

         this.evictIfNeeded();
      }
   }

   public void clear(String key) {
      if (key != null) {
         this.sessions.remove(key);
      }
   }

   public void clearAll() {
      this.sessions.clear();
   }

   private void evictIfNeeded() {
      if (this.sessions.size() > 512) {
         int toRemove = this.sessions.size() - 512;
         Iterator<Entry<String, Deque<ConversationMemory.Turn>>> it = this.sessions.entrySet().iterator();

         for (int i = 0; i < toRemove && it.hasNext(); i++) {
            it.next();
            it.remove();
         }
      }
   }

   public static record Turn(String role, String content) {
   }
}
