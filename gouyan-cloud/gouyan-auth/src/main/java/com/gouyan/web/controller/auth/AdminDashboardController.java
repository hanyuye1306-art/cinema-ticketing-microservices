package com.gouyan.web.controller.auth;

import com.gouyan.common.response.ResponseResult;
import com.gouyan.common.utils.JwtUtil;
import com.gouyan.system.domin.SysUser;
import com.gouyan.system.service.SysUserService;
import org.apache.shiro.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Aggregated dashboard data, scoped to the signed-in cinema manager when not a system admin. */
@RestController
public class AdminDashboardController {
    private final JdbcTemplate jdbc;
    private final SysUserService users;
    public AdminDashboardController(JdbcTemplate jdbc, SysUserService users) { this.jdbc = jdbc; this.users = users; }

    @GetMapping("/sysUser/dashboard")
    public ResponseResult dashboard(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Object principal = SecurityUtils.getSubject().getPrincipal();
        String name = principal == null ? null : JwtUtil.getUsername(principal.toString());
        SysUser user = name == null ? null : users.findByName(name);
        if (user == null || Long.valueOf(3).equals(user.getRoleId())) return ResponseResult.error(403, "无权访问管理看板");
        boolean administrator = Long.valueOf(1).equals(user.getRoleId());
        Long cinemaId = null;
        String cinemaName = "全部影院";
        if (!administrator) {
            List<Map<String, Object>> cinemas = jdbc.queryForList("select cinema_id, cinema_name from sys_cinema where user_id = ? limit 1", user.getUserId());
            cinemaId = cinemas.isEmpty() ? -1L : ((Number) cinemas.get(0).get("cinema_id")).longValue();
            cinemaName = cinemas.isEmpty() ? "尚未分配影院" : String.valueOf(cinemas.get(0).get("cinema_name"));
        }
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        String sessionScope = cinemaId == null ? "" : " and s.cinema_id = ?";
        String billScope = cinemaId == null ? "" : " and s.cinema_id = ?";
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("movies", jdbc.queryForObject("select count(*) from sys_movie", Long.class));
        counts.put("cinemas", cinemaId == null ? jdbc.queryForObject("select count(*) from sys_cinema", Long.class) : cinemaId > 0 ? 1 : 0);
        counts.put("halls", cinemaId == null ? jdbc.queryForObject("select count(*) from sys_hall", Long.class) : jdbc.queryForObject("select count(*) from sys_hall where cinema_id = ?", Long.class, cinemaId));
        counts.put("todaySessions", jdbc.queryForObject("select count(*) from sys_session s where s.session_date = ?" + sessionScope,
                Long.class, scoped(today.toString(), cinemaId)));
        counts.put("unpaidOrders", jdbc.queryForObject("select count(*) from sys_bill b join sys_session s on s.session_id = b.session_id where b.bill_state = 0" + billScope,
                Long.class, cinemaId == null ? new Object[0] : new Object[]{cinemaId}));
        counts.put("todayRevenue", jdbc.queryForObject("select coalesce(sum(s.session_price * case when json_valid(b.seats) then json_length(b.seats) else 0 end),0) "
                        + "from sys_bill b join sys_session s on s.session_id = b.session_id where b.bill_state = 1 and date(b.bill_date) = ?" + billScope,
                Double.class, scoped(today.toString(), cinemaId)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", administrator ? "all" : "cinema"); data.put("cinemaName", cinemaName); data.put("counts", counts);
        data.put("updatedAt", LocalDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("weeklyOrders", jdbc.queryForList("select date(b.bill_date) as day, count(*) as count from sys_bill b join sys_session s on s.session_id = b.session_id "
                        + "where date(b.bill_date) between ? and ?" + billScope + " group by date(b.bill_date) order by day",
                cinemaId == null ? new Object[]{today.minusDays(6).toString(), today.toString()} : new Object[]{today.minusDays(6).toString(), today.toString(), cinemaId}));
        data.put("movieAreas", jdbc.queryForList("select coalesce(a.movie_area_name,'未分类') as name, count(*) as count from sys_movie m left join sys_movie_area a on a.movie_area_id = m.movie_area_id "
                + "group by coalesce(a.movie_area_name,'未分类') order by count desc limit 5"));
        data.put("upcomingSessions", jdbc.queryForList("select s.session_id as sessionId, m.movie_name_cn as movieName, c.cinema_name as cinemaName, h.hall_name as hallName, "
                        + "s.session_date as sessionDate, r.begin_time as beginTime, s.session_price as price from sys_session s "
                        + "join sys_movie m on m.movie_id = s.movie_id join sys_cinema c on c.cinema_id = s.cinema_id "
                        + "join sys_hall h on h.cinema_id = s.cinema_id and h.hall_id = s.hall_id left join sys_movie_runtime r on r.movie_runtime_id = s.movie_runtime_id "
                        + "where s.session_state = 1 and s.session_date >= ?" + sessionScope + " order by s.session_date, r.begin_time, s.session_id limit 6",
                scoped(today.toString(), cinemaId)));
        data.put("recentMovies", jdbc.queryForList("select movie_id as movieId, movie_name_cn as name, movie_poster as poster, release_date as releaseDate, movie_score as score from sys_movie order by movie_id desc limit 4"));
        return ResponseResult.success(data);
    }

    private Object[] scoped(Object first, Long cinemaId) {
        return cinemaId == null ? new Object[]{first} : new Object[]{first, cinemaId};
    }
}
