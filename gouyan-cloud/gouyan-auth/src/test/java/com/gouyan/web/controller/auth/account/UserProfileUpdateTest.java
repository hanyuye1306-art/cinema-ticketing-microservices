package com.gouyan.web.controller.auth.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gouyan.common.utils.JwtUtil;
import com.gouyan.framework.exception.GlobalExceptionHandler;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.mapper.SysUserMapper;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import com.gouyan.web.controller.auth.SysUserController;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserProfileUpdateTest {
    @AfterEach
    void cleanUp() { ThreadContext.unbindSubject(); }

    @Test
    void profileWithoutPasswordPreservesLegacyHashAndSalt() {
        SysUser origin = origin(); origin.setPassword("legacy-hash"); origin.setSalt("legacy-salt");
        SysUser update = update();
        service(origin).update(update);
        assertEquals("legacy-hash", update.getPassword());
        assertEquals("legacy-salt", update.getSalt());
        assertEquals(origin.getSex(), update.getSex());
    }

    @Test
    void blankEditPasswordPreservesExistingBcryptHash() {
        SysUser origin = origin(); origin.setPassword(new BCryptPasswordEncoder().encode("old-secret"));
        SysUser update = update(); update.setPassword("");
        service(origin).update(update);
        assertEquals(origin.getPassword(), update.getPassword());
        assertNull(update.getSalt());
    }

    @Test
    void explicitNewPasswordIsBcryptEncodedAndLegacySaltRemoved() {
        SysUser origin = origin(); origin.setPassword("legacy-hash"); origin.setSalt("old-salt");
        SysUser update = update(); update.setPassword("new-secret");
        service(origin).update(update);
        assertTrue(new BCryptPasswordEncoder().matches("new-secret", update.getPassword()));
        assertNull(update.getSalt());
    }

    @Test
    void putWithoutPasswordPassesValidationAndDoesNotReturnCredentials() throws Exception {
        SysUserServiceImpl users = mock(SysUserServiceImpl.class);
        MockMvc mvc = mvc(users);
        when(users.update(any(SysUser.class))).thenReturn(1);
        mvc.perform(put("/sysUser").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":42,\"userName\":\"old-user\",\"email\":\"\",\"phoneNumber\":\"\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.salt").doesNotExist());
        verify(users).update(any(SysUser.class));
    }

    @Test
    void postStillRequiresPasswordAndPutRejectsShortNonblankPassword() throws Exception {
        SysUserServiceImpl users = mock(SysUserServiceImpl.class);
        MockMvc mvc = mvc(users);
        mvc.perform(post("/sysUser").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"new-user\"}"))
                .andExpect(jsonPath("$.code").value(500));
        mvc.perform(put("/sysUser").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":42,\"userName\":\"old-user\",\"password\":\"bad\"}"))
                .andExpect(jsonPath("$.code").value(500));
        verify(users, never()).add(any()); verify(users, never()).update(any());
    }

    @Test
    void selfRenameReturnsTokenForNewLoginName() throws Exception {
        SysUserServiceImpl users = mock(SysUserServiceImpl.class);
        MockMvc mvc = mvc(users); when(users.update(any())).thenReturn(1);
        String body = mvc.perform(put("/sysUser").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":42,\"userName\":\"new-user\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(body).get("data").get("token").asText();
        assertEquals("new-user", JwtUtil.getUsername(token));
        assertTrue(JwtUtil.verify(token, "new-user"));
    }

    private MockMvc mvc(SysUserServiceImpl users) {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(JwtUtil.sign("old-user")); ThreadContext.bind(subject);
        SysUser actor = origin(); actor.setRoleId(1L);
        when(users.findByName("old-user")).thenReturn(actor);
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sysUserService", users);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private SysUserServiceImpl service(SysUser origin) {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.findById(42L)).thenReturn(origin);
        when(mapper.findUsersByName(anyString())).thenReturn(Collections.singletonList(42L));
        when(mapper.update(any())).thenReturn(1);
        SysUserServiceImpl service = new SysUserServiceImpl(); ReflectionTestUtils.setField(service, "sysUserMapper", mapper);
        return service;
    }
    private SysUser origin() {
        SysUser user = new SysUser(); user.setUserId(42L); user.setUserName("old-user"); user.setSex(true); return user;
    }
    private SysUser update() {
        SysUser user = new SysUser(); user.setUserId(42L); user.setUserName("old-user"); return user;
    }
}
