package org.dromara.web.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.system.api.MessageService;
import org.dromara.system.controller.system.SysUserController;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysDeptService;
import org.dromara.system.service.ISysPostService;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
import org.dromara.web.config.AuthGrantProperties;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.service.AuthGrantPolicy;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthRouteTest {

    @BeforeAll
    static void initializeUtilities() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.registerBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
        context.registerBean(RedissonClient.class, () -> mock(RedissonClient.class));
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    @Test
    void registrationRouteIsAbsent() throws Exception {
        AuthController controller = new AuthController(mock(SysLoginService.class), defaultPolicy(),
            mock(ISysClientService.class), mock(ScheduledExecutorService.class), mock(MessageService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        int status = mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn().getResponse().getStatus();

        org.junit.jupiter.api.Assertions.assertEquals(404, status);
    }

    @Test
    void administratorCanStillAddUser() {
        ISysUserService users = mock(ISysUserService.class);
        ISysDeptService depts = mock(ISysDeptService.class);
        SysUserController controller = new SysUserController(users, mock(ISysRoleService.class),
            mock(ISysPostService.class), depts);
        SysUserBo user = new SysUserBo();
        user.setUserName("managed-user");
        user.setPassword("Example123!");
        user.setDeptId(1L);
        when(users.checkUserNameUnique(user)).thenReturn(true);
        when(users.insertUser(any(SysUserBo.class))).thenReturn(1);

        R<Void> response = controller.add(user);

        assertTrue(R.isSuccess(response));
        verify(depts).checkDeptDataScope(1L);
        verify(users).insertUser(user);
    }

    @Test
    void socialRoutesAreAbsent() throws Exception {
        AuthController controller = new AuthController(mock(SysLoginService.class), defaultPolicy(),
            mock(ISysClientService.class), mock(ScheduledExecutorService.class), mock(MessageService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        org.junit.jupiter.api.Assertions.assertEquals(404,
            mvc.perform(get("/auth/binding/example")).andReturn().getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(404,
            mvc.perform(post("/auth/social/callback")).andReturn().getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(404,
            mvc.perform(delete("/auth/unlock/invalid")).andReturn().getResponse().getStatus());
    }

    @Test
    void disabledSmsLoginStopsBeforeAuthentication() {
        ISysClientService clients = mock(ISysClientService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        SysClientVo client = new SysClientVo();
        client.setGrantType("password,sms,email");
        client.setStatus("0");
        when(clients.queryByClientId("client")).thenReturn(client);
        AuthController controller = new AuthController(mock(SysLoginService.class), defaultPolicy(), clients,
            scheduler, mock(MessageService.class));

        R<LoginVo> response = assertDoesNotThrow(() -> controller.login("{\"clientId\":\"client\",\"grantType\":\"sms\"}"));

        assertTrue(R.isError(response));
        verifyNoInteractions(scheduler);
    }

    @Test
    void disabledEmailLoginStopsBeforeAuthentication() {
        ISysClientService clients = mock(ISysClientService.class);
        SysClientVo client = new SysClientVo();
        client.setGrantType("password,sms,email");
        client.setStatus("0");
        when(clients.queryByClientId("client")).thenReturn(client);
        AuthController controller = new AuthController(mock(SysLoginService.class), defaultPolicy(), clients,
            mock(ScheduledExecutorService.class), mock(MessageService.class));

        R<LoginVo> response = assertDoesNotThrow(() -> controller.login("{\"clientId\":\"client\",\"grantType\":\"email\"}"));

        assertTrue(R.isError(response));
    }

    @Test
    void enabledSmsStillRequiresExactClientGrant() {
        ISysClientService clients = mock(ISysClientService.class);
        SysClientVo client = new SysClientVo();
        client.setGrantType("password,sms-extra");
        client.setStatus("0");
        when(clients.queryByClientId("client")).thenReturn(client);
        AuthController controller = new AuthController(mock(SysLoginService.class), enabledPolicy("sms"), clients,
            mock(ScheduledExecutorService.class), mock(MessageService.class));
        try (MockedStatic<IAuthStrategy> strategies = mockStatic(IAuthStrategy.class)) {
            R<LoginVo> response = controller.login("{\"clientId\":\"client\",\"grantType\":\"sms\"}");

            assertTrue(R.isError(response));
            strategies.verifyNoInteractions();
        }
    }

    @Test
    void enabledSmsWithClientGrantReachesStrategy() {
        ISysClientService clients = mock(ISysClientService.class);
        SysClientVo client = new SysClientVo();
        client.setGrantType("password,sms");
        client.setStatus("0");
        when(clients.queryByClientId("client")).thenReturn(client);
        AuthController controller = new AuthController(mock(SysLoginService.class), enabledPolicy("sms"), clients,
            mock(ScheduledExecutorService.class), mock(MessageService.class));
        IllegalStateException reached = new IllegalStateException("strategy reached");
        try (MockedStatic<IAuthStrategy> strategies = mockStatic(IAuthStrategy.class)) {
            strategies.when(() -> IAuthStrategy.login(any(String.class), any(SysClientVo.class), any(String.class)))
                .thenThrow(reached);

            assertEquals(reached, assertThrows(IllegalStateException.class,
                () -> controller.login("{\"clientId\":\"client\",\"grantType\":\"sms\"}")));
        }
    }

    @Test
    void disabledSmsCodeNeverCallsSenderOrCache() {
        CaptchaController controller = new CaptchaController(mock(CaptchaProperties.class),
            mock(MailProperties.class), defaultPolicy());
        try (MockedStatic<SmsFactory> sms = mockStatic(SmsFactory.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            R<Void> response = assertDoesNotThrow(() -> controller.smsCode("13800138000"));

            assertTrue(R.isError(response));
            sms.verifyNoInteractions();
            redis.verifyNoInteractions();
        }
    }

    @Test
    void disabledEmailCodeNeverCallsSenderOrCache() {
        MailProperties mail = mock(MailProperties.class);
        when(mail.getEnabled()).thenReturn(true);
        CaptchaController controller = new CaptchaController(mock(CaptchaProperties.class), mail, defaultPolicy());
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            R<Void> response = assertDoesNotThrow(() -> controller.emailCode("person@example.com"));

            assertTrue(R.isError(response));
            redis.verifyNoInteractions();
        }
    }

    @Test
    void enabledCodeRoutesReachTheirExistingProviderChecks() {
        MailProperties mail = mock(MailProperties.class);
        CaptchaController controller = new CaptchaController(mock(CaptchaProperties.class), mail,
            enabledPolicy("sms", "email"));

        assertEquals("请输入正确的手机号！", controller.smsCode("invalid").getMsg());
        assertEquals("当前系统没有开启邮箱功能！", controller.emailCode("person@example.com").getMsg());
    }

    private static AuthGrantPolicy defaultPolicy() {
        return new AuthGrantPolicy(new AuthGrantProperties());
    }

    private static AuthGrantPolicy enabledPolicy(String... grants) {
        AuthGrantProperties properties = new AuthGrantProperties();
        properties.setEnabledGrantTypes(Set.of(grants));
        return new AuthGrantPolicy(properties);
    }
}
