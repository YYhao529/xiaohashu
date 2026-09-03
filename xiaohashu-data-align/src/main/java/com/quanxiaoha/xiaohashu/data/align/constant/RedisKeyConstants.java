package com.quanxiaoha.xiaohashu.data.align.constant;

public class RedisKeyConstants {

    /**
     * 布隆过滤器：日增量变更数据，笔记点赞数 前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_LIST_KEY = "bloom:dataAlign:note:likes:";

    /**
     * 布隆过滤器：日增量变更数据，用户获得点赞数 前缀
     */
    public static final String BLOOM_TODAY_USER_LIKE_LIST_KEY = "bloom:dataAlign:user:likes:";

    /**
     * 布隆过滤器：日增量变更数据，笔记收藏状态 前缀
     */
    public static final String BLOOM_TODAY_NOTE_COLLECT_LIST_KEY = "bloom:dataAlign:note:collects:";

    /**
     * 布隆过滤器：日增量变更数据，用户被收藏数 前缀
     */
    public static final String BLOOM_TODAY_USER_COLLECT_LIST_KEY = "bloom:dataAlign:user:collects:";

    /**
     * 布隆过滤器：日增量变更数据，用户笔记发布，删除 前缀
     */
    public static final String BLOOM_TODAY_USER_NOTE_OPERATOR_LIST_KEY = "bloom:dataAlign:user:note:operators:";

    /**
     * 布隆过滤器：日增量变更数据，用户关注数 前缀
     */
    public static final String BLOOM_TODAY_USER_FOLLOW_LIST_KEY = "bloom:dataAlign:user:follows:";

    /**
     * 布隆过滤器：日增量变更数据，用户粉丝数 前缀
     */
    public static final String BLOOM_TODAY_USER_FANS_LIST_KEY = "bloom:dataAlign:user:fans:";

    /**
     * 用户维度计数 Key 前缀
     */
    private static final String COUNT_USER_KEY_PREFIX = "count:user:";

    /**
     * Hash Field: 关注总数
     */
    public static final String FIELD_FOLLOWING_TOTAL = "followingTotal";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，笔记点赞数 KEY
     */
    public static String buildBloomNoteLikeListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户获得点赞数 KEY
     */
    public static String buildBloomUserLikeListKey(String date) {
        return BLOOM_TODAY_USER_LIKE_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，笔记收藏状态 KEY
     */
    public static String buildBloomNoteCollectListKey(String date) {
        return BLOOM_TODAY_NOTE_COLLECT_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户被收藏数 KEY
     */
    public static String buildBloomUserCollectListKey(String date) {
        return BLOOM_TODAY_USER_COLLECT_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记发布，删除 KEY
     */
    public static String buildBloomUserNoteOperateListKey(String date) {
        return BLOOM_TODAY_USER_NOTE_OPERATOR_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户关注数 KEY
     */
    public static String buildBloomUserFollowListKey(String date) {
        return BLOOM_TODAY_USER_FOLLOW_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户粉丝数 KEY
     */
    public static String buildBloomUserFansListKey(String date) {
        return BLOOM_TODAY_USER_FANS_LIST_KEY + date;
    }

    /**
     * 构建用户维度计数 Key
     */
    public static String buildCountUserKey(Long userId) {
        return COUNT_USER_KEY_PREFIX + userId;
    }

}
