package com.gouyan.system.service.impl;

import com.gouyan.system.domin.SysResource;
import com.gouyan.system.mapper.SysResourceMapper;
import com.gouyan.system.service.SysResourceService;
import com.gouyan.common.exception.DataNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SysResourceServiceImpl implements SysResourceService {
    @Autowired
    private AdminDeleteGuard adminDeleteGuard;


    @Autowired
    private SysResourceMapper sysResourceMapper;

    @Override
    public List<SysResource> findAll() {
        return sysResourceMapper.findAll();
    }

    @Override
    public List<SysResource> findWithChildren() {
        return sysResourceMapper.findWithChildren();
    }

    @Override
    public List<SysResource> findAllWithAllChildren() {
        return sysResourceMapper.findAllWithAllChildren();
    }

    @Override
    public SysResource findById(Long id) {
        return sysResourceMapper.findById(id);
    }

    @Override
    public int add(SysResource sysResource) {
        validateParent(sysResource);
        return sysResourceMapper.add(sysResource);
    }

    @Override
    public int update(SysResource sysResource) {
        validateParent(sysResource);
        log.debug(sysResource.toString());
        return sysResourceMapper.update(sysResource);
    }

    private void validateParent(SysResource resource) {
        Long parentId = resource.getParentId();
        if (parentId == null || parentId == 0) {
            resource.setParentId(0L);
            resource.setLevel(1);
            return;
        }
        java.util.Set<Long> visited = new java.util.HashSet<>();
        if (resource.getId() != null) visited.add(resource.getId());
        SysResource parent = null;
        Long cursor = parentId;
        while (cursor != null && cursor != 0) {
            if (!visited.add(cursor)) throw new DataNotFoundException("菜单父级不能形成循环");
            SysResource current = findById(cursor);
            if (current == null) throw new DataNotFoundException("所选父级菜单不存在");
            if (parent == null) parent = current;
            cursor = current.getParentId();
        }
        resource.setLevel(parent.getLevel() + 1);
        // Reparenting a non-leaf would also require updating descendant levels.
        if (resource.getId() != null) {
            SysResource original = findById(resource.getId());
            if (original != null && !java.util.Objects.equals(original.getParentId(), parentId)
                    && findAll().stream().anyMatch(item -> resource.getId().equals(item.getParentId()))) {
                throw new DataNotFoundException("请先移动子菜单，再调整父级");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long[] ids) {
        adminDeleteGuard.check("sysResource", ids);
        int rows = 0;
        for(Long id : ids){
            rows += sysResourceMapper.delete(id);
        }
        return rows;
    }
}
