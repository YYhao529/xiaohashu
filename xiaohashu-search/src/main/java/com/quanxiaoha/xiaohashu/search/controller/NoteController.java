package com.quanxiaoha.xiaohashu.search.controller;

import com.quanxiaoha.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.search.model.vo.FindHotNoteReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteRspVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchUserReqVO;
import com.quanxiaoha.xiaohashu.search.service.NoteService;
import com.quanxiaoha.xiaohashu.search.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 笔记搜索
 */
@RestController
@RequestMapping("/search")
@Slf4j
public class NoteController {

    @Autowired
    private NoteService noteService;

    @PostMapping("/note")
    @ApiOperationLog(description = "搜索笔记")
    public PageResponse<SearchNoteRspVO> searchNote(@RequestBody @Validated SearchNoteReqVO searchNoteReqVO) {
        return noteService.searchNote(searchNoteReqVO);
    }

    @PostMapping("/note/hot")
    @ApiOperationLog(description = "查询热门笔记")
    public PageResponse<SearchNoteRspVO> findHotNoteList(@RequestBody @Validated FindHotNoteReqVO findHotNoteReqVO) {
        return noteService.findHotNoteList(findHotNoteReqVO);
    }

    @PostMapping("/note/rebuild")
    @ApiOperationLog(description = "重建笔记索引")
    public Response<Integer> rebuildNoteIndex() {
        return noteService.rebuildNoteIndex();
    }

}
