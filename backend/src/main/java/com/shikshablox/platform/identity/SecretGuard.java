package com.shikshablox.platform.identity;

import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Refuses to start a staging/production profile with the development secrets. */
@Component
class SecretGuard implements InitializingBean {

  private final IdentityProperties props;
  private final Environment env;

  SecretGuard(IdentityProperties props, Environment env) {
    this.props = props;
    this.env = env;
  }

  @Override
  public void afterPropertiesSet() {
    boolean deployed =
        Arrays.stream(env.getActiveProfiles())
            .anyMatch(p -> p.equals("staging") || p.equals("prod"));
    if (deployed
        && (props.jwtSecret().contains("dev-only") || props.dataKey().contains("dev-only"))) {
      throw new IllegalStateException(
          "Development secrets are not allowed in staging/prod: set JWT_SECRET and SB_DATA_KEY");
    }
  }
}
