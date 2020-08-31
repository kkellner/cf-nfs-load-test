package net.hyperboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@Configuration
@EnableAsync
class ApplicationReadyListener {
 
  private static final Logger logger = LoggerFactory.getLogger(ApplicationReadyListener.class);
  
  @Autowired
  CreateNfsTraffic nfsTraffic;

  @EventListener
  @Async
  public void onApplicationReadyEvent(ApplicationReadyEvent event) {
    logger.info("ApplicationListener#onApplicationEvent()");
    nfsTraffic.begin();
  }

  
  @EventListener
  public void onApplicationEvent(ContextClosedEvent event) {
    logger.info("App Shutdown event: {}", event);
    nfsTraffic.stop();
  }
  
}