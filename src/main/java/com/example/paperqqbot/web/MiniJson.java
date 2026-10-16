package com.example.paperqqbot.web;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

final class MiniJson {
   private MiniJson() {
   }

   static String toJson(Object o) {
      StringBuilder sb = new StringBuilder(256);
      write(sb, o);
      return sb.toString();
   }

   private static void write(StringBuilder sb, Object o) {
      if (o == null) {
         sb.append("null");
      } else if (o instanceof String s) {
         writeString(sb, s);
      } else if (!(o instanceof Number) && !(o instanceof Boolean)) {
         if (o instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;

            for (Entry<?, ?> e : map.entrySet()) {
               if (!first) {
                  sb.append(',');
               }

               first = false;
               writeString(sb, String.valueOf(e.getKey()));
               sb.append(':');
               write(sb, e.getValue());
            }

            sb.append('}');
         } else if (o instanceof Collection c) {
            sb.append('[');
            boolean first = true;

            for (Object item : c) {
               if (!first) {
                  sb.append(',');
               }

               first = false;
               write(sb, item);
            }

            sb.append(']');
         } else if (o.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(o);

            for (int i = 0; i < len; i++) {
               if (i > 0) {
                  sb.append(',');
               }

               write(sb, Array.get(o, i));
            }

            sb.append(']');
         } else {
            writeString(sb, String.valueOf(o));
         }
      } else {
         sb.append(o.toString());
      }
   }

   private static void writeString(StringBuilder sb, String s) {
      sb.append('"');

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

      sb.append('"');
   }
}
