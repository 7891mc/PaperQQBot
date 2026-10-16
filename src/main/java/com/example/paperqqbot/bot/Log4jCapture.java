package com.example.paperqqbot.bot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

public final class Log4jCapture implements AutoCloseable {
   private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
   private final LoggerContext context = (LoggerContext)LogManager.getContext(false);
   private final AbstractAppender appender;

   public Log4jCapture(String appenderName) {
      this.appender = new AbstractAppender(appenderName, null, null, true, Property.EMPTY_ARRAY) {
         {
            Objects.requireNonNull(Log4jCapture.this);
         }

         public void append(LogEvent event) {
            if (event != null && event.getMessage() != null) {
               String message = event.getMessage().getFormattedMessage();
               if (message != null && !message.isBlank()) {
                  Log4jCapture.this.lines.add(message);
               }
            }
         }
      };
      this.appender.start();
      this.context.getConfiguration().getRootLogger().addAppender(this.appender, Level.INFO, null);
      this.context.updateLoggers();
   }

   public List<String> lines() {
      return this.lines;
   }

   @Override
   public void close() {
      try {
         this.context.getConfiguration().getRootLogger().removeAppender(this.appender.getName());
         this.context.updateLoggers();
      } finally {
         this.appender.stop();
      }
   }
}
