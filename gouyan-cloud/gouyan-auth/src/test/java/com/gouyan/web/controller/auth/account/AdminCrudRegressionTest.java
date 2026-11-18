package com.gouyan.web.controller.auth.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gouyan.common.exception.DataNotFoundException;
import com.gouyan.system.domin.*;
import com.gouyan.system.mapper.*;
import com.gouyan.system.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.text.SimpleDateFormat;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminCrudRegressionTest {
    @Test
    void movieDateAcceptsTheDatePickerFormatDespiteGlobalTimestampFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper().setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        SysMovie movie = mapper.readValue("{\"releaseDate\":\"2026-11-18\"}", SysMovie.class);
        assertNotNull(movie.getReleaseDate());
        assertTrue(mapper.writeValueAsString(movie).contains("2026-11-18"));
    }

    @Test
    void topLevelMenuWithNoParentIsNormalizedToRoot() {
        SysResourceMapper mapper = mock(SysResourceMapper.class);
        SysResourceServiceImpl service = new SysResourceServiceImpl();
        ReflectionTestUtils.setField(service, "sysResourceMapper", mapper);
        SysResource resource = new SysResource(); resource.setName("test-root");
        when(mapper.add(resource)).thenReturn(1);
        assertEquals(1, service.add(resource));
        assertEquals(0L, resource.getParentId()); assertEquals(1, resource.getLevel());
    }

    @Test
    void unchangedEmptyPermissionSelectionIsStillSuccessful() {
        SysRoleMapper mapper = mock(SysRoleMapper.class);
        SysRoleServiceImpl service = new SysRoleServiceImpl();
        ReflectionTestUtils.setField(service, "sysRoleMapper", mapper);
        when(mapper.findById(42L)).thenReturn(new SysRole());
        when(mapper.findAllRights(42L)).thenReturn(Collections.emptyList());
        assertEquals(1, service.allotRight(42L, new Long[0]));
        verify(mapper, never()).addRight(anyLong(), anyLong());
    }

    @Test
    void emptyDeletionAndBuiltinRoleDeletionAreRejectedBeforeSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AdminDeleteGuard guard = new AdminDeleteGuard(jdbc);
        assertThrows(DataNotFoundException.class, () -> guard.check("sysMovie", new Long[0]));
        assertThrows(DataNotFoundException.class, () -> guard.check("sysRole", new Long[]{1L}));
        verifyNoInteractions(jdbc);
    }

    @Test
    void referencedActorStopsTheWholeBatchBeforeDeletingAnyRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(1);
        SysActorMapper mapper = mock(SysActorMapper.class);
        SysActorServiceImpl service = new SysActorServiceImpl();
        ReflectionTestUtils.setField(service, "sysActorMapper", mapper);
        ReflectionTestUtils.setField(service, "adminDeleteGuard", new AdminDeleteGuard(jdbc));
        assertThrows(DataNotFoundException.class, () -> service.delete(new Long[]{41L, 42L}));
        verify(mapper, never()).delete(anyLong());
    }

    @Test
    void hallDeletionChecksItsCompositeKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(1L))).thenReturn(1);
        SysHall hall = new SysHall(); hall.setCinemaId(42L); hall.setHallId(1L);
        assertThrows(DataNotFoundException.class, () -> new AdminDeleteGuard(jdbc).checkHalls(new SysHall[]{hall}));
    }
}
