package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysCinema;
import com.gouyan.system.domin.vo.SysCinemaVo;
import com.gouyan.system.mapper.SysCinemaMapper;
import com.gouyan.system.service.SysCinemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysCinemaServiceImpl implements SysCinemaService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    private SysCinemaMapper sysCinemaMapper;


    @Override
    // 查询对象同时承载影院属性与影厅类别条件。Service 将筛选条件交给 Mapper，分页由入口调用链负责。
    public List<SysCinema> findAll(SysCinemaVo sysCinemaVo) {
        return sysCinemaMapper.findAll(sysCinemaVo);
    }

    @Override
    // 按影院主键读取基础详情。SysCinemaResult 会补充用户、品牌、区域与影厅类别关联。
    public SysCinema findById(Long id) {
        return sysCinemaMapper.findById(id);
    }

    @Override
    // 返回 Mapper 的影响行数。
    // 当前 XML 的 selectKey 回填 cinemaAreaId，调试新对象时需留意它可能覆盖原区域 ID。
    public int add(SysCinema sysCinema) {
        return sysCinemaMapper.add(sysCinema);
    }

    @Override
    // XML 按 cinemaId 定位记录，并显式写入其他列。提交表单时应携带需要保留的字段值。
    public int update(SysCinema sysCinema) {
        return sysCinemaMapper.update(sysCinema);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 删除前统一调用 AdminDeleteGuard 检查关联数据，随后逐 ID 删除并累加影响行数。
    // 异常传播到事务代理时，回滚本次批量删除。
    public int delete(Long[] ids) {
        adminDeleteGuard.check("sysCinema", ids);
        int rows = 0;
        for (Long id : ids) {
            rows += sysCinemaMapper.delete(id);
        }
        return rows;
    }

    @Override
    // 此详情查询使用 SysCinemaMap，映射中按影院 ID 加载关联电影集合，供影院详情组装。
    public SysCinema findCinemaById(Long id) {
        return sysCinemaMapper.findCinemaById(id);
    }
}
