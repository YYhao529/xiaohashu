package com.quanxiaoha.xiaohashu.note.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteLikeDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteLikeDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteLikeDO record);

    int insertSelective(NoteLikeDO record);

    NoteLikeDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteLikeDO record);

    int updateByPrimaryKey(NoteLikeDO record);

    /**
     * 查询笔记是否点赞
     */
    int selectCountByUserIdAndNoteId(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询当前用户所有点赞的笔记
     */
    List<NoteLikeDO> selectByUserId(Long userId);

    /**
     * 查询笔记是否被用户点赞
     */
    int selectNoteIsLiked(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询用户最新点赞的笔记
     */
    List<NoteLikeDO> selectLikedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 新增笔记点赞记录，若已存在，则更新笔记点赞记录
     */
    int insertOrUpdate(NoteLikeDO noteLikeDO);

    /**
     * 取消点赞
     */
    int update2UnlikeByUserIdAndNoteId(NoteLikeDO noteLikeDO);
}