package com.shikshablox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ShikshaBlox modular monolith.
 *
 * <p>Application modules are declared explicitly with {@code @ApplicationModule} in each module's
 * {@code package-info.java} (see {@code spring.modulith.detection-strategy}). Platform modules live
 * under {@code com.shikshablox.platform.*}; product modules (M01 website, etc.) under {@code
 * com.shikshablox.modules.*}. Boundaries are verified by {@code ModularityTests}.
 */
@Modulithic(systemName = "ShikshaBlox", sharedModules = "shared")
@SpringBootApplication
@EnableScheduling
public class ShikshaBloxApplication {

  public static void main(String[] args) {
    SpringApplication.run(ShikshaBloxApplication.class, args);
  }
}
