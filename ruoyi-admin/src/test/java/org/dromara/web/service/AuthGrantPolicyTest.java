package org.dromara.web.service;

import org.dromara.web.config.AuthGrantProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthGrantPolicyTest {

    @Test
    void onlyExactPasswordGrantIsEnabledByDefault() {
        AuthGrantPolicy policy = new AuthGrantPolicy(new AuthGrantProperties());

        assertTrue(policy.isEnabled("password"));
        for (String disabled : new String[]{"sms", "email", "social", "xcx", "unknown",
            "", "password-extra", "PASSWORD", " password", "password "}) {
            assertFalse(policy.isEnabled(disabled), disabled);
        }
        assertFalse(policy.isEnabled(null));
    }

    @Test
    void clientGrantMustBeAnExactCommaSeparatedValue() {
        AuthGrantPolicy policy = new AuthGrantPolicy(new AuthGrantProperties());

        assertTrue(policy.isClientEnabled("password,sms", "password"));
        assertTrue(policy.isClientEnabled("password,sms", "sms"));
        assertFalse(policy.isClientEnabled("password-extra,social", "password"));
        assertFalse(policy.isClientEnabled("PASSWORD,sms", "password"));
        assertFalse(policy.isClientEnabled("password, sms", "sms"));
        assertFalse(policy.isClientEnabled(null, "password"));
        assertFalse(policy.isClientEnabled("password", null));
    }

    @Test
    void explicitServerConfigurationCanEnableSmsAndEmail() {
        AuthGrantProperties properties = new AuthGrantProperties();
        properties.setEnabledGrantTypes(Set.of("password", "sms", "email"));
        AuthGrantPolicy policy = new AuthGrantPolicy(properties);

        assertTrue(policy.isEnabled("sms"));
        assertTrue(policy.isEnabled("email"));
        assertFalse(policy.isEnabled("social"));
    }
}
