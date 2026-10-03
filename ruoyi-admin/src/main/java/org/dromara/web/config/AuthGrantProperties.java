package org.dromara.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 服务端允许的登录方式。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthGrantProperties {

    private Set<String> enabledGrantTypes = Set.of("password");
}
