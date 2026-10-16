package com.example.paperqqbot.bot;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.config.LlmSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LlmClient {
   private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private final PaperQQBotPlugin plugin;
   private final HttpClient httpClient;

   public LlmClient(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   }

   public String chat(String userMessage, List<ConversationMemory.Turn> history) {
      LlmSettings cfg = this.plugin.config().getLlm();
      if (!cfg.enabled()) {
         return null;
      } else if (cfg.hasApiUrl() && cfg.hasApiKey()) {
         String body = buildRequestBody(cfg, userMessage, history);

         try {
            Builder builder = HttpRequest.newBuilder()
               .uri(URI.create(cfg.apiUrl()))
               .timeout(Duration.ofSeconds((long)Math.max(5, cfg.timeoutSeconds())))
               .header("Content-Type", "application/json; charset=UTF-8")
               .header("Authorization", "Bearer " + cfg.apiKey())
               .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            HttpResponse<String> resp = this.httpClient.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
               String content = extractContent(resp.body());
               if (content != null && !content.isBlank()) {
                  return content.trim();
               } else {
                  this.plugin.pluginLogger().warning("[LLM] 响应中未找到 content 字段：" + truncate(resp.body()));
                  return null;
               }
            } else {
               this.plugin.pluginLogger().warning("[LLM] HTTP " + resp.statusCode() + " body=" + truncate(resp.body()));
               return null;
            }
         } catch (Exception var8) {
            this.plugin.pluginLogger().log(Level.WARNING, "[LLM] 调用失败：" + var8.getMessage(), (Throwable)var8);
            return null;
         }
      } else {
         this.plugin.pluginLogger().warning("[LLM] api-url 或 api-key 未配置，跳过调用");
         return null;
      }
   }

   private static String buildRequestBody(LlmSettings cfg, String userMessage, List<ConversationMemory.Turn> history) {
      String systemPrompt = cfg.systemPrompt() == null ? "" : cfg.systemPrompt();
      StringBuilder messages = new StringBuilder();
      messages.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");
      if (history != null) {
         for (ConversationMemory.Turn t : history) {
            String role = "assistant".equals(t.role()) ? "assistant" : "user";
            messages.append(",{\"role\":\"").append(role).append("\",\"content\":\"").append(escapeJson(t.content())).append("\"}");
         }
      }

      messages.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(userMessage)).append("\"}");
      return "{\"model\":\"" + escapeJson(cfg.model()) + "\",\"temperature\":" + cfg.temperature() + ",\"messages\":[" + messages + "]}";
   }

   private static String extractContent(String json) {
      if (json == null) {
         return null;
      } else {
         Matcher m = CONTENT_PATTERN.matcher(json);
         return m.find() ? unescapeJson(m.group(1)) : null;
      }
   }

   private static String escapeJson(String s) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(s.length() + 16);

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append("\\n");
                  break;
               case '\r':
                  sb.append("\\r");
                  break;
               case '"':
                  sb.append("\\\"");
                  break;
               case '\\':
                  sb.append("\\\\");
                  break;
               default:
                  if (c < ' ') {
                     sb.append(String.format("\\u%04x", c));
                  } else {
                     sb.append(c);
                  }
            }
         }

         return sb.toString();
      }
   }

   private static String unescapeJson(String s) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '\\' && i + 1 < s.length()) {
            char esc = s.charAt(++i);
            switch (esc) {
               case '"':
                  sb.append('"');
                  break;
               case '/':
                  sb.append('/');
                  break;
               case '\\':
                  sb.append('\\');
                  break;
               case 'n':
                  sb.append('\n');
                  break;
               case 'r':
                  sb.append('\r');
                  break;
               case 't':
                  sb.append('\t');
                  break;
               case 'u':
                  if (i + 4 < s.length()) {
                     String hex = s.substring(i + 1, i + 5);
                     i += 4;
                     sb.append((char)Integer.parseInt(hex, 16));
                  }
                  break;
               default:
                  sb.append(esc);
            }
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   private static String truncate(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() > 500 ? s.substring(0, 500) + "..." : s;
      }
   }
}
