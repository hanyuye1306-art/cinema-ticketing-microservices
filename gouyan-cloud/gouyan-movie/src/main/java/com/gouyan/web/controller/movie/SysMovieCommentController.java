package com.gouyan.web.controller.movie;

import com.gouyan.common.constant.HttpStatus;
import com.gouyan.common.exception.DataNotFoundException;
import com.gouyan.common.response.ResponseResult;
import com.gouyan.system.domin.SysMovie;
import com.gouyan.system.domin.SysMovieComment;
import com.gouyan.system.service.impl.SysMovieCommentServiceImpl;
import com.gouyan.system.service.impl.SysMovieServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;

@RestController
public class SysMovieCommentController extends BaseController {

    @Autowired private JdbcTemplate jdbc;

    @Autowired
    private SysMovieCommentServiceImpl sysMovieCommentService;

    @Autowired
    private SysMovieServiceImpl sysMovieService;

    @GetMapping("/sysMovieComment")
    public ResponseResult findAll(){
        startPage();
        List<SysMovieComment> data = sysMovieCommentService.findAll();
        return getResult(data);
    }

    @GetMapping("/sysMovieComment/{movieId}")
    public ResponseResult findByMovieId(@PathVariable Long movieId){
        return getResult(sysMovieCommentService.findByMovieId(movieId));
    }

    @GetMapping("/sysUserComment/{userId}")
    public ResponseResult findByUserId(@PathVariable Long userId){
        return getResult(sysMovieCommentService.findByUserId(userId));
    }

    @PostMapping("/sysMovieComment")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult add(@Validated @RequestBody SysMovieComment comment) {
        lockMovie(comment.getMovieId());
        validateScore(comment);
        int rows = sysMovieCommentService.add(comment);
        refreshRating(comment.getMovieId());
        return getResult(rows);
    }

    @PutMapping("/sysMovieComment")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult update(@Validated @RequestBody SysMovieComment comment) {
        lockMovie(comment.getMovieId());
        validateScore(comment);
        if (sysMovieCommentService.findOne(comment) == null) throw new DataNotFoundException("原评论不存在，请刷新列表");
        int rows = sysMovieCommentService.update(comment);
        refreshRating(comment.getMovieId());
        return getResult(rows);
    }

    @PostMapping("/sysMovieComment/delete")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult delete(@RequestBody SysMovieComment[] pks) {
        if (pks == null || pks.length == 0) throw new DataNotFoundException("请选择需要删除的评论");
        java.util.TreeSet<Long> movieIds = new java.util.TreeSet<>();
        for (SysMovieComment pk : pks) {
            if (pk == null || pk.getMovieId() == null) throw new DataNotFoundException("评论标识无效");
            movieIds.add(pk.getMovieId());
        }
        movieIds.forEach(this::lockMovie);
        for (SysMovieComment pk : pks) {
            if (sysMovieCommentService.findOne(pk) == null) throw new DataNotFoundException("评论已不存在，请刷新列表");
        }
        int rows = sysMovieCommentService.delete(pks);
        movieIds.forEach(this::refreshRating);
        return getResult(rows);
    }

    private void validateScore(SysMovieComment comment) {
        if (comment.getScore() == null || !Double.isFinite(comment.getScore()) || comment.getScore() < 0 || comment.getScore() > 10)
            throw new DataNotFoundException("评分必须在 0 到 10 之间");
    }

    private void lockMovie(Long movieId) {
        if (movieId == null || jdbc.queryForList("select movie_id from sys_movie where movie_id = ? for update", movieId).isEmpty())
            throw new DataNotFoundException("影片不存在");
    }

    private void refreshRating(Long movieId) {
        Map<String, Object> stats = jdbc.queryForMap("select count(*) as total, coalesce(avg(score),0) as rating from sys_movie_comment where movie_id = ?", movieId);
        jdbc.update("update sys_movie set movie_rate_num = ?, movie_score = ? where movie_id = ?", stats.get("total"), stats.get("rating"), movieId);
    }
}
