package org.dromara.web.service;

import lombok.RequiredArgsConstructor;
import org.dromara.web.config.AuthGrantProperties;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;

/**
 * 登录方式的服务端与客户端授权检查。
 */
@Service
@RequiredArgsConstructor
public class AuthGrantPolicy {

    private final AuthGrantProperties properties;

    public boolean isEnabled(String grantType) {
        Set<String> enabled = properties.getEnabledGrantTypes();
        return grantType != null && enabled != null && enabled.contains(grantType);
    }

    public boolean isClientEnabled(String clientGrantTypes, String grantType) {
        return clientGrantTypes != null && grantType != null
            && Arrays.asList(clientGrantTypes.split(",", -1)).contains(grantType);
    }
}
