package com.quanxiaoha.xiaohashu.data.align.constant;

public class RedisKeyConstants {

    /**
     * 布隆过滤器：日增量变更数据，笔记点赞数 前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_LIST_KEY = "bloom:dataAlign:note:likes:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，笔记点赞数 KEY
     */
    public static String buildBloomNoteLikeListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，用户获得点赞数 前缀
     */
    public static final String BLOOM_TODAY_USER_LIKE_LIST_KEY = "bloom:dataAlign:user:likes:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户获得点赞数 KEY
     */
    public static String buildBloomUserLikeListKey(String date) {
        return BLOOM_TODAY_USER_LIKE_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，笔记收藏状态 前缀
     */
    public static final String BLOOM_TODAY_NOTE_COLLECT_LIST_KEY = "bloom:dataAlign:note:collects:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，笔记收藏状态 KEY
     */
    public static String buildBloomNoteCollectListKey(String date) {
        return BLOOM_TODAY_NOTE_COLLECT_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，用户被收藏数 前缀
     */
    public static final String BLOOM_TODAY_USER_COLLECT_LIST_KEY = "bloom:dataAlign:user:collects:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户被收藏数 KEY
     */
    public static String buildBloomUserCollectListKey(String date) {
        return BLOOM_TODAY_USER_COLLECT_LIST_KEY + date;
    }
}
