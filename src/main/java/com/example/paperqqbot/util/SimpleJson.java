package com.example.paperqqbot.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {
   private SimpleJson() {
   }

   public static Object parse(String src) {
      return new SimpleJson.Parser(src).parse();
   }

   public static Map<String, Object> parseObject(String src) {
      Object o = parse(src);
      return (Map<String, Object>)(o instanceof Map ? (Map)o : new LinkedHashMap<>());
   }

   public static Map<String, Object> asObject(Object o) {
      Map map;
      return o instanceof Map ? (map = (Map)o) : Map.of();
   }

   public static List<Object> asList(Object o) {
      return (List<Object>)(o instanceof List list ? new ArrayList<>(list) : List.of());
   }

   public static String getString(Map<String, Object> map, String key) {
      if (map == null) {
         return "";
      } else {
         Object v = map.get(key);
         return v == null ? "" : String.valueOf(v);
      }
   }

   public static long getLong(Map<String, Object> map, String key, long def) {
      if (map == null) {
         return def;
      } else {
         Object v = map.get(key);
         if (v instanceof Number n) {
            return n.longValue();
         } else if (v == null) {
            return def;
         } else {
            try {
               return Long.parseLong(String.valueOf(v).trim());
            } catch (NumberFormatException var6) {
               return def;
            }
         }
      }
   }

   public static double getDouble(Map<String, Object> map, String key, double def) {
      if (map == null) {
         return def;
      } else {
         Object v = map.get(key);
         if (v instanceof Number n) {
            return n.doubleValue();
         } else if (v == null) {
            return def;
         } else {
            try {
               return Double.parseDouble(String.valueOf(v).trim());
            } catch (NumberFormatException var6) {
               return def;
            }
         }
      }
   }

   public static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
      if (map == null) {
         return def;
      } else {
         Object v = map.get(key);
         if (v instanceof Boolean b) {
            return b;
         } else {
            return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
         }
      }
   }

   public static String escape(String s) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(s.length() + 16);

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
               case '\b':
                  sb.append("\\b");
                  break;
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append("\\n");
                  break;
               case '\f':
                  sb.append("\\f");
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

   public static String deepFindString(Object node, String key) {
      return deepFind(node, key, 0);
   }

   private static String deepFind(Object node, String key, int depth) {
      if (depth <= 6 && node != null) {
         if (!(node instanceof Map map)) {
            if (node instanceof List) {
               for (Object item : (List)node) {
                  String hit = deepFind(item, key, depth + 1);
                  if (hit != null) {
                     return hit;
                  }
               }
            }
         } else {
            if (map.get(key) instanceof String s && !s.isEmpty()) {
               return s;
            }

            for (Object v : map.values()) {
               String hit = deepFind(v, key, depth + 1);
               if (hit != null) {
                  return hit;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static final class Parser {
      private final String src;
      private int pos;

      Parser(String src) {
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
               case 'f', 't' -> this.parseBoolean();
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

      private boolean parseBoolean() {
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
