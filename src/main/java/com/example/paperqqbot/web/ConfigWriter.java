package com.example.paperqqbot.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

final class ConfigWriter {
   private ConfigWriter() {
   }

   static String toYaml(String json) {
      Object root = new ConfigWriter.MiniParser(json).parse();
      if (!(root instanceof Map)) {
         throw new IllegalArgumentException("根节点必须是对象");
      } else {
         return toYamlFromMap((Map<String, Object>)root);
      }
   }

   static String toYamlFromMap(Map<String, Object> root) {
      StringBuilder sb = new StringBuilder(2048);
      sb.append("# PaperQQBot 配置文件，由 WebUI 生成\n");
      writeYaml(sb, "", root, 0);
      return sb.toString();
   }

   private static void writeYaml(StringBuilder sb, String prefix, Map<String, Object> map, int depth) {
      for (Entry<String, Object> e : map.entrySet()) {
         String k = e.getKey();
         Object v = e.getValue();
         if (v instanceof Map) {
            sb.append(prefix).append(quoteKey(k)).append(":\n");
            writeYaml(sb, prefix + "  ", (Map<String, Object>)v, depth + 1);
         } else if (v instanceof List list) {
            sb.append(prefix).append(quoteKey(k)).append(":\n");
            writeList(sb, prefix + "  ", list);
         } else {
            writeScalarValue(sb, prefix, k, v);
         }
      }
   }

   private static void writeList(StringBuilder sb, String prefix, List<?> list) {
      if (list.isEmpty()) {
         sb.append(prefix).append("[]\n");
      } else {
         for (Object item : list) {
            if (item instanceof Map) {
               Map im = (Map)item;
               sb.append(prefix).append("-");
               boolean first = true;

               for (Object ieObj : im.entrySet()) {
                  Entry<?, ?> ie = (Entry<?, ?>)ieObj;
                  Object entryPrefix = first ? " " : prefix + "  ";
                  String key = String.valueOf(ie.getKey());
                  Object val = ie.getValue();
                  if (val instanceof Map) {
                     sb.append((String)entryPrefix).append(quoteKey(key)).append(":\n");
                     writeYaml(sb, (String)entryPrefix + "  ", (Map<String, Object>)val, 0);
                  } else if (val instanceof List subList) {
                     sb.append((String)entryPrefix).append(quoteKey(key)).append(":\n");
                     writeList(sb, (String)entryPrefix + "  ", subList);
                  } else {
                     writeScalarValue(sb, (String)entryPrefix, key, val);
                  }

                  first = false;
               }
            } else if (item instanceof List subList) {
               sb.append(prefix).append("-\n");
               writeList(sb, prefix + "  ", subList);
            } else {
               sb.append(prefix).append("- ").append(scalar(item)).append('\n');
            }
         }
      }
   }

   private static void writeScalarValue(StringBuilder sb, String prefix, String key, Object v) {
      if (isMultilineBlock(v)) {
         sb.append(prefix).append(quoteKey(key)).append(": |\n");

         for (String line : String.valueOf(v).split("\n", -1)) {
            sb.append(prefix).append("  ").append(line).append('\n');
         }
      } else {
         sb.append(prefix).append(quoteKey(key)).append(": ").append(scalar(v)).append('\n');
      }
   }

   private static boolean isMultilineBlock(Object v) {
      return !(v instanceof String s) ? false : s.indexOf(10) >= 0;
   }

   private static String quoteKey(String k) {
      if (k.matches("[A-Za-z0-9_-]+")) {
         return k;
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append('"');

         for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == '"' || c == '\\') {
               sb.append('\\');
            }

            sb.append(c);
         }

         sb.append('"');
         return sb.toString();
      }
   }

   private static String scalar(Object v) {
      if (v == null) {
         return "null";
      } else if (v instanceof Boolean) {
         return v.toString();
      } else if (v instanceof Number) {
         return v.toString();
      } else {
         String s = String.valueOf(v);
         if (!s.isEmpty()
            && !s.equals("null")
            && !s.equals("true")
            && !s.equals("false")
            && !s.matches("-?[0-9]+(\\.[0-9]+)?")
            && s.indexOf(10) < 0
            && s.indexOf(58) < 0
            && s.indexOf(35) < 0
            && !s.startsWith(" ")
            && !s.endsWith(" ")
            && !s.startsWith("-")
            && !s.startsWith("[")
            && !s.startsWith("{")
            && !s.startsWith("*")
            && !s.startsWith("&")
            && !s.startsWith("!")
            && !s.startsWith("|")
            && !s.startsWith(">")
            && !s.startsWith("%")
            && !s.startsWith("@")
            && !s.startsWith("`")
            && !s.startsWith(",")) {
            return s;
         } else {
            StringBuilder sb = new StringBuilder();
            sb.append('"');

            for (int i = 0; i < s.length(); i++) {
               char c = s.charAt(i);
               if (c == '"' || c == '\\') {
                  sb.append('\\');
               }

               sb.append(c);
            }

            sb.append('"');
            return sb.toString();
         }
      }
   }

   static final class MiniParser {
      private final String src;
      private int pos;

      MiniParser(String src) {
         this.src = src == null ? "" : src.trim();
      }

      Object parse() {
         this.skipWs();
         Object v = this.parseValue();
         this.skipWs();
         return v;
      }

      private Object parseValue() {
         this.skipWs();
         if (this.pos >= this.src.length()) {
            throw new IllegalArgumentException("JSON 结尾非预期");
         } else {
            char c = this.src.charAt(this.pos);

            return switch (c) {
               case '"' -> this.parseString();
               case '[' -> this.parseArray();
               case 'f', 't' -> this.parseBool();
               case 'n' -> this.parseNull();
               case '{' -> this.parseObject();
               default -> this.parseNumber();
            };
         }
      }

      private Map<String, Object> parseObject() {
         this.expect('{');
         this.skipWs();
         LinkedHashMap<String, Object> map = new LinkedHashMap<>();
         if (this.peek() == '}') {
            this.pos++;
            return map;
         } else {
            char c;
            do {
               this.skipWs();
               String key = this.parseString();
               this.skipWs();
               this.expect(':');
               Object val = this.parseValue();
               map.put(key, val);
               this.skipWs();
            } while ((c = this.next()) == ',');

            if (c != '}') {
               throw new IllegalArgumentException("解析对象失败 pos=" + this.pos);
            } else {
               return map;
            }
         }
      }

      private List<Object> parseArray() {
         this.expect('[');
         this.skipWs();
         ArrayList<Object> list = new ArrayList<>();
         if (this.peek() == ']') {
            this.pos++;
            return list;
         } else {
            char c;
            do {
               Object v = this.parseValue();
               list.add(v);
               this.skipWs();
            } while ((c = this.next()) == ',');

            if (c != ']') {
               throw new IllegalArgumentException("解析数组失败 pos=" + this.pos);
            } else {
               return list;
            }
         }
      }

      private String parseString() {
         this.expect('"');
         StringBuilder sb = new StringBuilder();

         while (this.pos < this.src.length()) {
            char c;
            if ((c = this.src.charAt(this.pos++)) == '"') {
               return sb.toString();
            }

            if (c == '\\' && this.pos < this.src.length()) {
               char esc = this.src.charAt(this.pos++);
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
                     if (this.pos + 4 <= this.src.length()) {
                        String hex = this.src.substring(this.pos, this.pos + 4);
                        this.pos += 4;
                        sb.append((char)Integer.parseInt(hex, 16));
                     } else {
                        sb.append('u');
                     }
                     break;
                  default:
                     sb.append(esc);
               }
            } else {
               sb.append(c);
            }
         }

         throw new IllegalArgumentException("字符串未闭合");
      }

      private Object parseNumber() {
         int start = this.pos;

         char c;
         while (
            this.pos < this.src.length() && ((c = this.src.charAt(this.pos)) == '-' || c == '+' || c >= '0' && c <= '9' || c == '.' || c == 'e' || c == 'E')
         ) {
            this.pos++;
         }

         String num = this.src.substring(start, this.pos);
         if (!num.contains(".") && !num.contains("e") && !num.contains("E")) {
            try {
               return Long.parseLong(num);
            } catch (NumberFormatException var5) {
               return Double.parseDouble(num);
            }
         } else {
            return Double.parseDouble(num);
         }
      }

      private boolean parseBool() {
         if (this.src.startsWith("true", this.pos)) {
            this.pos += 4;
            return true;
         } else if (this.src.startsWith("false", this.pos)) {
            this.pos += 5;
            return false;
         } else {
            throw new IllegalArgumentException("非法布尔值 pos=" + this.pos);
         }
      }

      private Object parseNull() {
         if (this.src.startsWith("null", this.pos)) {
            this.pos += 4;
            return null;
         } else {
            throw new IllegalArgumentException("非法 null 值 pos=" + this.pos);
         }
      }

      private void skipWs() {
         char c;
         while (this.pos < this.src.length() && ((c = this.src.charAt(this.pos)) == ' ' || c == '\t' || c == '\n' || c == '\r')) {
            this.pos++;
         }
      }

      private char peek() {
         if (this.pos >= this.src.length()) {
            throw new IllegalArgumentException("JSON 已结束");
         } else {
            return this.src.charAt(this.pos);
         }
      }

      private char next() {
         if (this.pos >= this.src.length()) {
            throw new IllegalArgumentException("JSON 已结束");
         } else {
            return this.src.charAt(this.pos++);
         }
      }

      private void expect(char expected) {
         if (this.pos < this.src.length() && this.src.charAt(this.pos) == expected) {
            this.pos++;
         } else {
            throw new IllegalArgumentException("期望 '" + expected + "' pos=" + this.pos);
         }
      }
   }
}
