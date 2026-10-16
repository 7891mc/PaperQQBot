package com.example.paperqqbot.bot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OneBotEventParser {
   private static final Pattern POST_TYPE = Pattern.compile("\"post_type\"\\s*:\\s*\"([^\"]+)\"");
   private static final Pattern MESSAGE_TYPE = Pattern.compile("\"message_type\"\\s*:\\s*\"([^\"]+)\"");
   private static final Pattern GROUP_ID = Pattern.compile("\"group_id\"\\s*:\\s*\"?([0-9]+)\"?");
   private static final Pattern USER_ID = Pattern.compile("\"user_id\"\\s*:\\s*\"?([0-9]+)\"?");
   private static final Pattern SELF_ID = Pattern.compile("\"self_id\"\\s*:\\s*\"?([0-9]+)\"?");
   private static final Pattern MESSAGE_ID = Pattern.compile("\"message_id\"\\s*:\\s*\"?([0-9]+)\"?");
   private static final Pattern AT_ARRAY = Pattern.compile(
      "\\{[^{}]*\"type\"\\s*:\\s*\"at\"[^{}]*\"data\"\\s*:\\s*\\{[^{}]*\"qq\"\\s*:\\s*\"?([0-9]+)\"?[^{}]*\\}[^{}]*\\}"
   );
   private static final Pattern TEXT_ARRAY = Pattern.compile(
      "\\{[^{}]*\"type\"\\s*:\\s*\"text\"[^{}]*\"data\"\\s*:\\s*\\{[^{}]*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"[^{}]*\\}[^{}]*\\}"
   );
   private static final Pattern AT_CQ = Pattern.compile("\\[CQ:at,qq=([0-9]+)[^\\]]*\\]");
   private static final Pattern MESSAGE_FIELD = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private static final Pattern SENDER_BLOCK = Pattern.compile("\"sender\"\\s*:\\s*(\\{[^{}]*\\})");
   private static final Pattern NICKNAME = Pattern.compile("\"nickname\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private static final Pattern CARD = Pattern.compile("\"card\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private static final Pattern ROLE = Pattern.compile("\"role\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private static final Pattern MEDIA_ARRAY = Pattern.compile("\\{[^{}]*\"type\"\\s*:\\s*\"(image|record|video)\"[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");
   private static final Pattern MEDIA_CQ = Pattern.compile("\\[CQ:(image|record|video)[^\\]]*\\]");

   private OneBotEventParser() {
   }

   public static OneBotEventParser.GroupMessageEvent parseGroupMessage(String json) {
      if (json != null && !json.isEmpty()) {
         Matcher pt = POST_TYPE.matcher(json);
         if (pt.find() && "message".equals(pt.group(1))) {
            Matcher mt = MESSAGE_TYPE.matcher(json);
            if (mt.find() && "group".equals(mt.group(1))) {
               Long groupId = firstLong(GROUP_ID, json);
               Long userId = firstLong(USER_ID, json);
               Long selfId = firstLong(SELF_ID, json);
               if (groupId == null) {
                  return null;
               } else {
                  String messageId = firstString(MESSAGE_ID, json, "");
                  List<Long> atTargets = extractAtTargets(json);
                  String text = extractPlainText(json);
                  String senderName = extractSenderName(json);
                  String senderRole = extractSenderRole(json);
                  List<String> media = extractMedia(json);
                  return new OneBotEventParser.GroupMessageEvent(
                     groupId, userId != null ? userId : 0L, selfId != null ? selfId : 0L, atTargets, text, messageId, senderName, senderRole, media
                  );
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String extractSenderRole(String json) {
      Matcher sender = SENDER_BLOCK.matcher(json);
      Matcher role;
      return sender.find() && (role = ROLE.matcher(sender.group(1))).find() ? unescapeJson(role.group(1)) : "";
   }

   private static Long firstLong(Pattern p, String json) {
      Matcher m = p.matcher(json);
      if (m.find()) {
         try {
            return Long.parseLong(m.group(1));
         } catch (NumberFormatException var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String firstString(Pattern p, String json, String def) {
      Matcher m = p.matcher(json);
      return m.find() ? unescapeJson(m.group(1)) : def;
   }

   private static List<Long> extractAtTargets(String json) {
      LinkedHashSet<Long> set = new LinkedHashSet<>();
      Matcher m = AT_ARRAY.matcher(json);

      while (m.find()) {
         try {
            set.add(Long.valueOf(Long.parseLong(m.group(1))));
         } catch (NumberFormatException var6) {
         }
      }

      if (set.isEmpty()) {
         Matcher cq = AT_CQ.matcher(json);

         while (cq.find()) {
            try {
               set.add(Long.valueOf(Long.parseLong(cq.group(1))));
            } catch (NumberFormatException var5) {
            }
         }
      }

      return new ArrayList<>(set);
   }

   private static List<String> extractMedia(String json) {
      LinkedHashSet<String> types = new LinkedHashSet<>();
      Matcher m = MEDIA_ARRAY.matcher(json);

      while (m.find()) {
         types.add(m.group(1));
      }

      if (types.isEmpty()) {
         Matcher cq = MEDIA_CQ.matcher(json);

         while (cq.find()) {
            types.add(cq.group(1));
         }
      }

      ArrayList<String> result = new ArrayList<>();

      for (String var6 : types) {
         result.add(switch (var6) {
            case "image" -> "[图片]";
            case "record" -> "[语音]";
            case "video" -> "[视频]";
            default -> "";
         });
      }

      return result;
   }

   private static String extractSenderName(String json) {
      Matcher sender = SENDER_BLOCK.matcher(json);
      if (sender.find()) {
         String block = sender.group(1);
         Matcher card = CARD.matcher(block);
         if (card.find() && !card.group(1).isBlank()) {
            return unescapeJson(card.group(1));
         }

         Matcher nick2 = NICKNAME.matcher(block);
         if (nick2.find()) {
            return unescapeJson(nick2.group(1));
         }
      }

      Matcher nick;
      return (nick = NICKNAME.matcher(json)).find() ? unescapeJson(nick.group(1)) : "";
   }

   private static String extractPlainText(String json) {
      StringBuilder sb = new StringBuilder();
      Matcher tm = TEXT_ARRAY.matcher(json);
      boolean foundArrayText = false;

      while (tm.find()) {
         foundArrayText = true;
         sb.append(unescapeJson(tm.group(1)));
      }

      if (foundArrayText) {
         return sb.toString().trim();
      } else {
         Matcher mf = MESSAGE_FIELD.matcher(json);
         if (mf.find()) {
            String raw = mf.group(1);
            raw = raw.replaceAll("\\[CQ:[^\\]]*\\]", "");
            return unescapeJson(raw).trim();
         } else {
            return "";
         }
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
               case 'b':
                  sb.append('\b');
                  break;
               case 'f':
                  sb.append('\f');
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

   public static record GroupMessageEvent(
      long groupId, long userId, long selfId, List<Long> atTargets, String text, String messageId, String senderName, String senderRole, List<String> media
   ) {
   }
}
