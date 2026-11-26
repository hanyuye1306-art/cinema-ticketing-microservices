package com.gouyan.web.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gouyan.common.page.Page;
import com.gouyan.common.page.PageBuilder;
import com.gouyan.common.response.ResponseResult;
import static com.gouyan.common.page.PageBuilder.*;

import java.util.List;

/**
 * 抽取重复功能为基类
 */
public class BaseController {

    /**
     * 开启分页
     */
    // 页码和页大小都存在时，给当前线程的下一次 MyBatis 查询设置分页与排序。
    // 追踪列表接口时，应核对 startPage 与实际列表查询之间是否执行了其他查询。
    public void startPage(){
        Page page = PageBuilder.buildPage();
        Integer pageNum = page.getPageNum();
        Integer pageSize = page.getPageSize();
        if(pageNum != null && pageSize != null){
            PageHelper.startPage(pageNum, pageSize, page.getOrderByColumn());
        }
    }

    /**
     * 根据修改行数返回响应消息
     * @param rows
     * @return
     */
    // 此重载以影响行数判断业务响应。零行时可结合 Mapper 的 WHERE 条件核查。
    public ResponseResult getResult(int rows){
        return rows == 0 ? ResponseResult.error() : ResponseResult.success();
    }

    /**
     * 分页响应消息
     * @param data
     * @return
     */
    // 从 PageInfo 提取页码、页大小和总数，与业务数据一起返回供前端分页组件使用。
    public ResponseResult getResult(List<?> data){
        PageInfo pageInfo = new PageInfo(data);
        ResponseResult responseResult = ResponseResult.success(data);
        responseResult.put(PAGE_NUM, pageInfo.getPageNum());
        responseResult.put(PAGE_SIZE, pageInfo.getPageSize());
        responseResult.put(TOTAL, pageInfo.getTotal());
        return responseResult;
    }

    /**
     * 对象类型响应消息
     * @param data
     * @return
     */
    public ResponseResult getResult(Object data){
        return ResponseResult.success(data);
    }

}
