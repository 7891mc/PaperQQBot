package com.example.paperqqbot.account;

import com.example.paperqqbot.PaperQQBotPlugin;
import com.example.paperqqbot.util.SimpleJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class AccountClient {
   private final PaperQQBotPlugin plugin;
   private final HttpClient http;

   public AccountClient(PaperQQBotPlugin plugin) {
      this.plugin = plugin;
      this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).version(Version.HTTP_1_1).build();
   }

   public AccountClient.Result bind(String code) {
      LinkedHashMap<String, Object> outer = new LinkedHashMap<>();
      outer.put("action", "bind");
      outer.put("instance", this.plugin.accountStore().instanceId());
      outer.put("secret", this.plugin.accountStore().clientSecret());
      outer.put("code", code);
      return this.post(outer, null);
   }

   public AccountClient.Result validate() {
      return this.signed("validate", Map.of());
   }

   public AccountClient.Result authPassword(String email, String password) {
      LinkedHashMap<String, Object> inner = new LinkedHashMap<>();
      inner.put("email", email);
      inner.put("password", password);
      return this.signed("auth_password", inner);
   }

   public AccountClient.Result mailCode(String email) {
      return this.signed("mailcode", Map.of("email", email));
   }

   public AccountClient.Result report(Map<String, Object> fields) {
      if (this.plugin.accountStore().isBound()) {
         return this.signed("report", fields);
      } else {
         LinkedHashMap<String, Object> outer = new LinkedHashMap<>();
         outer.put("action", "report");
         outer.put("instance", this.plugin.accountStore().instanceId());
         outer.put("fields", fields);
         return this.post(outer, null);
      }
   }

   private AccountClient.Result signed(String action, Map<String, Object> innerParams) {
      byte[] key = PayloadCrypto.keyFromSecret(this.plugin.accountStore().clientSecret());
      String innerJson = toJson(innerParams);
      String secure = PayloadCrypto.encrypt(innerJson, key);
      long ts = System.currentTimeMillis() / 1000L;
      String sig = PayloadCrypto.sign(key, ts, secure);
      LinkedHashMap<String, Object> outer = new LinkedHashMap<>();
      outer.put("action", action);
      outer.put("instance", this.plugin.accountStore().instanceId());
      outer.put("ts", Long.valueOf(ts));
      outer.put("secure", secure);
      outer.put("sig", sig);
      return this.post(outer, key);
   }

   private AccountClient.Result post(Map<String, Object> outer, byte[] decryptKey) {
      AccountClient.Result result = new AccountClient.Result();

      try {
         String body = toJson(outer);
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(this.plugin.config().accountApiBase()))
            .timeout(Duration.ofSeconds(10L))
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
         HttpResponse<String> resp = this.http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
         Map<String, Object> rj = SimpleJson.parseObject(resp.body());
         result.outer = rj;
         Object okObj = rj.get("ok");
         Boolean b;
         boolean bl = result.ok = okObj instanceof Boolean && b = (Boolean)okObj;
         if (!result.ok) {
            result.error = SimpleJson.getString(rj, "error");
            return result;
         } else {
            Object sec = rj.get("secure");
            String decrypted;
            String s;
            if (sec instanceof String
               && !(s = (String)sec).isBlank()
               && (
                     decrypted = PayloadCrypto.decrypt(
                        s, decryptKey != null ? decryptKey : PayloadCrypto.keyFromSecret(this.plugin.accountStore().clientSecret())
                     )
                  )
                  != null) {
               result.secure = SimpleJson.parseObject(decrypted);
            }

            return result;
         }
      } catch (Exception var15) {
         result.ok = false;
         result.error = "无法连接账号服务：" + var15.getMessage();
         return result;
      }
   }

   private static String toJson(Object o) {
      if (o == null) {
         return "null";
      } else if (o instanceof String s) {
         return "\"" + SimpleJson.escape(s) + "\"";
      } else if (!(o instanceof Boolean) && !(o instanceof Number)) {
         if (o instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;

            for (Entry<?, ?> e : map.entrySet()) {
               if (!first) {
                  sb.append(',');
               }

               first = false;
               sb.append('"').append(SimpleJson.escape(String.valueOf(e.getKey()))).append("\":").append(toJson(e.getValue()));
            }

            return sb.append('}').toString();
         } else {
            return "\"" + SimpleJson.escape(String.valueOf(o)) + "\"";
         }
      } else {
         return String.valueOf(o);
      }
   }

   public static final class Result {
      public boolean ok;
      public String error = "";
      public Map<String, Object> outer = Map.of();
      public Map<String, Object> secure = Map.of();

      public String str(String k, String def) {
         Object v = this.secure.get(k);
         return v == null ? def : String.valueOf(v);
      }

      public boolean bool(String k, boolean def) {
         boolean bl;
         if (this.secure.get(k) instanceof Boolean b) {
            bl = b;
         } else {
            bl = def;
         }

         return bl;
      }
   }
}
