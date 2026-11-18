package com.gouyan.web.controller.auth.account;

import com.gouyan.common.utils.JwtUtil;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.service.impl.SysUserServiceImpl;
import com.gouyan.web.controller.auth.SysUserController;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAccessTest {
    private SysUserServiceImpl users;
    private SysUserController controller;
    @BeforeEach
    void setUp() {
        users = mock(SysUserServiceImpl.class); controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sysUserService", users);
    }
    @AfterEach
    void cleanUp() { ThreadContext.unbindSubject(); }

    @Test
    void regularAccountCannotReadAnotherAccount() {
        signIn(3L);
        assertThrows(AuthenticationException.class, () -> controller.findById(99L));
        verify(users, never()).findById(any());
    }
    @Test
    void selfUpdateCannotElevateRole() {
        signIn(3L);
        SysUser update = new SysUser(); update.setUserId(42L); update.setRoleId(1L);
        when(users.update(any(SysUser.class))).thenReturn(1);
        controller.update(update);
        assertEquals(3L, update.getRoleId());
    }
    @Test
    void regularAccountCannotCreateOrDeleteOtherUsers() {
        signIn(3L);
        assertThrows(AuthenticationException.class, () -> controller.add(new SysUser()));
        assertThrows(AuthenticationException.class, () -> controller.delete(new Long[]{99L}));
        verify(users, never()).add(any()); verify(users, never()).delete(any());
    }
    @Test
    void administratorCanStillCreateUsers() {
        signIn(1L);
        when(users.add(any(SysUser.class))).thenReturn(1);
        assertEquals(200, controller.add(new SysUser()).get("code"));
    }
    private void signIn(Long role) {
        SysUser actor = new SysUser(); actor.setUserId(42L); actor.setRoleId(role);
        when(users.findByName("test-user")).thenReturn(actor);
        Subject subject = mock(Subject.class); when(subject.getPrincipal()).thenReturn(JwtUtil.sign("test-user"));
        ThreadContext.bind(subject);
    }
}
