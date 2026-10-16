package com.example.paperqqbot.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PayloadCrypto {
   private static final SecureRandom RNG = new SecureRandom();

   private PayloadCrypto() {
   }

   public static byte[] keyFromSecret(String secret) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         return md.digest(secret.getBytes(StandardCharsets.UTF_8));
      } catch (Exception var2) {
         throw new IllegalStateException("无法派生密钥", var2);
      }
   }

   public static String encrypt(String plaintext, byte[] key) {
      try {
         byte[] iv = new byte[16];
         RNG.nextBytes(iv);
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(1, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
         byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
         byte[] mac = hmac(key, concat(iv, ct));
         return Base64.getUrlEncoder().withoutPadding().encodeToString(concat(iv, ct, mac));
      } catch (Exception var6) {
         throw new IllegalStateException("加密失败", var6);
      }
   }

   public static String decrypt(String token, byte[] key) {
      try {
         byte[] raw = Base64.getUrlDecoder().decode(token);
         if (raw.length < 48) {
            return null;
         } else {
            byte[] mac = Arrays.copyOfRange(raw, raw.length - 32, raw.length);
            byte[] body = Arrays.copyOfRange(raw, 0, raw.length - 32);
            byte[] iv = Arrays.copyOfRange(body, 0, 16);
            byte[] ct = Arrays.copyOfRange(body, 16, body.length);
            byte[] expect = hmac(key, body);
            if (!MessageDigest.isEqual(expect, mac)) {
               return null;
            } else {
               Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
               cipher.init(2, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
               return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            }
         }
      } catch (Exception var9) {
         return null;
      }
   }

   public static String sign(byte[] key, long ts, String data) {
      try {
         byte[] mac = hmac(key, (ts + "." + data).getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(mac);
      } catch (Exception var5) {
         throw new IllegalStateException("签名失败", var5);
      }
   }

   private static byte[] hmac(byte[] key, byte[] data) throws Exception {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
   }

   private static byte[] concat(byte[]... parts) {
      int total = 0;

      for (byte[] p : parts) {
         total += p.length;
      }

      byte[] out = new byte[total];
      int off = 0;

      for (byte[] p : parts) {
         System.arraycopy(p, 0, out, off, p.length);
         off += p.length;
      }

      return out;
   }
}
