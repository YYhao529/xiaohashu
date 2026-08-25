package com.quanxiaoha.xiaohashu.note.biz.constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /**
     * 笔记详情 KEY 前缀
     */
    private static final String NOTE_DETAIL_KEY = "note:detail:";

    /**
     * 构建完整的笔记详情 KEY
     */
    public static String buildNoteDetailKey(Long noteId) {
        return NOTE_DETAIL_KEY + noteId;
    }
}
