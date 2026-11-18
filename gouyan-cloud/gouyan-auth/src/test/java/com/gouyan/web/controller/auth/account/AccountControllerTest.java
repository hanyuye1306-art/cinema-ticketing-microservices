package com.gouyan.web.controller.auth.account;

import com.gouyan.framework.config.ShiroConfig;
import com.gouyan.framework.exception.GlobalExceptionHandler;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerTest {
    private AccountService service;
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingCaptchaStopsRegistrationBeforeCallingService() throws Exception {
        mvc.perform(post("/sysUser/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"new-user\",\"email\":\"owner@example.invalid\",\"password\":\"new-secret\",\"confirmPassword\":\"new-secret\",\"sex\":false}"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
    }

    @Test
    void invalidEmailAndMissingCodeCannotReachResetService() throws Exception {
        mvc.perform(post("/sysUser/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"new-user\",\"email\":\"not-an-email\",\"password\":\"new-secret\",\"confirmPassword\":\"new-secret\"}"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
    }

    @Test
    void sendResponseDoesNotExposeCodeOrAccountExistence() throws Exception {
        mvc.perform(post("/sysUser/password/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"new-user\",\"email\":\"owner@example.invalid\",\"captchaVerification\":\"proof\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void onlyExplicitPublicAccountEndpointsBypassAuthentication() {
        Map<String, String> rules = new ShiroConfig()
                .getShiroFilterFactoryBean(new DefaultWebSecurityManager()).getFilterChainDefinitionMap();
        assertEquals("anon", rules.get("/sysUser/register"));
        assertEquals("anon", rules.get("/sysUser/password/code"));
        assertEquals("anon", rules.get("/sysUser/password/reset"));
        assertFalse(rules.containsKey("/sysUser/**"));
        assertEquals("jwt", rules.get("/**"));
    }
}
