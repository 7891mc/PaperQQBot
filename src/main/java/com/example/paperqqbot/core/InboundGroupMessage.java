package com.example.paperqqbot.core;

import java.util.List;

public record InboundGroupMessage(
   String groupKey,
   long userQq,
   String memberOpenid,
   String authorName,
   String text,
   List<String> attachments,
   long selfId,
   String messageId,
   boolean atBot,
   boolean passiveAvailable,
   boolean ownerRole
) {
   public String identityKey() {
      return this.memberOpenid != null ? this.memberOpenid : "qq:" + this.userQq;
   }

   public String displayContent() {
      StringBuilder sb = new StringBuilder(this.text == null ? "" : this.text);
      if (this.attachments != null) {
         for (String a : this.attachments) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(a);
         }
      }

      return sb.toString();
   }
}
