package com.gouyan.system.service.impl;

import com.gouyan.common.exception.DataNotFoundException;
import com.gouyan.system.domin.SysHall;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** All names are internal constants; record IDs are bound parameters, never SQL identifiers. */
@Component
public class AdminDeleteGuard {
    private final JdbcTemplate jdbc;
    private static final Map<String, String[][]> REFERENCES = new HashMap<>();
    static {
        REFERENCES.put("sysActor", new String[][]{{"sys_actor_movie", "actor_id", "电影演员关联"}});
        REFERENCES.put("sysActorRole", new String[][]{{"sys_actor_movie", "actor_role_id", "电影演员关联"}});
        REFERENCES.put("sysCinemaArea", new String[][]{{"sys_cinema", "cinema_area_id", "影院"}});
        REFERENCES.put("sysCinemaBrand", new String[][]{{"sys_cinema", "cinema_brand_id", "影院"}});
        REFERENCES.put("sysCinema", new String[][]{{"sys_hall", "cinema_id", "影厅"}, {"sys_session", "cinema_id", "场次"}});
        REFERENCES.put("sysHallCategory", new String[][]{{"sys_hall", "hall_category_id", "影厅"}});
        REFERENCES.put("sysMovieAge", new String[][]{{"sys_movie", "movie_age_id", "电影"}});
        REFERENCES.put("sysMovieArea", new String[][]{{"sys_movie", "movie_area_id", "电影"}});
        REFERENCES.put("sysMovieCategory", new String[][]{{"sys_movie_to_category", "movie_category_id", "电影分类关联"}});
        REFERENCES.put("sysMovieRuntime", new String[][]{{"sys_session", "movie_runtime_id", "场次"}});
        REFERENCES.put("sysMovie", new String[][]{{"sys_session", "movie_id", "场次"}, {"sys_movie_comment", "movie_id", "影评"},
                {"sys_actor_movie", "movie_id", "演员关联"}, {"sys_movie_to_category", "movie_id", "电影分类关联"}});
        REFERENCES.put("sysSession", new String[][]{{"sys_bill", "session_id", "订单"}});
        REFERENCES.put("sysUser", new String[][]{{"sys_bill", "user_id", "订单"}, {"sys_cinema", "user_id", "影院负责人"}, {"sys_movie_comment", "user_id", "影评"}});
        REFERENCES.put("sysRole", new String[][]{{"sys_user", "role_id", "用户"}});
        REFERENCES.put("sysResource", new String[][]{{"sys_resource", "parent_id", "子菜单"}, {"sys_role_resource", "resource_id", "角色权限"}});
    }
    public AdminDeleteGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void check(String entity, Long[] ids) {
        if (ids == null || ids.length == 0) throw new DataNotFoundException("请先选择要删除的数据");
        for (Long id : ids) {
            if (id == null) throw new DataNotFoundException("删除参数不完整");
            if ("sysRole".equals(entity) && id >= 1 && id <= 3) throw new DataNotFoundException("系统内置角色不能删除");
            for (String[] ref : REFERENCES.getOrDefault(entity, new String[0][])) {
                Integer count = jdbc.queryForObject("select count(*) from " + ref[0] + " where " + ref[1] + " = ?", Integer.class, id);
                if (count != null && count > 0) throw new DataNotFoundException("该记录仍有关联的" + ref[2] + "，请先处理关联数据");
            }
        }
    }

    public void checkHalls(SysHall[] halls) {
        if (halls == null || halls.length == 0) throw new DataNotFoundException("请先选择要删除的影厅");
        for (SysHall hall : halls) {
            if (hall == null || hall.getCinemaId() == null || hall.getHallId() == null) throw new DataNotFoundException("影厅编号不完整");
            Integer count = jdbc.queryForObject("select count(*) from sys_session where cinema_id = ? and hall_id = ?",
                    Integer.class, hall.getCinemaId(), hall.getHallId());
            if (count != null && count > 0) throw new DataNotFoundException("该影厅仍有关联场次，请先处理场次");
        }
    }

    public void removeRoleRights(Long[] ids) {
        for (Long id : ids) jdbc.update("delete from sys_role_resource where role_id = ?", id);
    }

    public boolean hasSessionOrders(Long id) {
        Integer count = jdbc.queryForObject("select count(*) from sys_bill where session_id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public void checkHallLayout(Long cinemaId, Long hallId) {
        Integer count = jdbc.queryForObject("select count(*) from sys_session s where s.cinema_id = ? and s.hall_id = ? "
                + "and (s.seat_nums > 0 or exists(select 1 from sys_bill b where b.session_id = s.session_id))",
                Integer.class, cinemaId, hallId);
        if (count != null && count > 0) throw new DataNotFoundException("该影厅已有订单，不能重排座位；名称和分类仍可修改");
    }
}
