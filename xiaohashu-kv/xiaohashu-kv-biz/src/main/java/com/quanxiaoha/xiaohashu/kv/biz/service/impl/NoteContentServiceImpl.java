package com.quanxiaoha.xiaohashu.kv.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.biz.domain.domain.dataobject.NoteContentDO;
import com.quanxiaoha.xiaohashu.kv.biz.domain.domain.repository.NoteContentRepository;
import com.quanxiaoha.xiaohashu.kv.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.kv.biz.service.NoteContentService;
import com.quanxiaoha.xiaohashu.kv.dto.req.AddNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.req.DeleteNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.req.FindNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rsp.FindNoteContentRspDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 笔记内容存储业务实现
 */
@Service
@Slf4j
public class NoteContentServiceImpl implements NoteContentService {

    @Autowired
    private NoteContentRepository noteContentRepository;

    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        // 笔记 UUID
        String uuid = addNoteContentReqDTO.getUuid();
        // 笔记内容
        String content = addNoteContentReqDTO.getContent();

        // 构建数据库 DO 实体类
        NoteContentDO noteContentDO = NoteContentDO.builder()
                .id(UUID.fromString(uuid))
                .content(content)
                .build();

        // 插入数据
        noteContentRepository.save(noteContentDO);

        return Response.success();
    }

    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        // 笔记 UUID
        String uuid = findNoteContentReqDTO.getUuid();
        // 根据笔记 UUID 查询笔记内容
        Optional<NoteContentDO> option = noteContentRepository.findById(UUID.fromString(uuid));

        // 若笔记内容不存在
        if(option.isEmpty()){
           throw new BizException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }

        NoteContentDO noteContentDO = option.get();
        // 构建返参 DTO
        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                .uuid(noteContentDO.getId())
                .content(noteContentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        // 笔记 UUID
        String uuid = deleteNoteContentReqDTO.getUuid();
        // 根据笔记 UUID 删除笔记内容
        noteContentRepository.deleteById(UUID.fromString(uuid));

        return Response.success();
    }
}
