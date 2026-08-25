package com.quanxiaoha.xiaohashu.note.biz.constant;

public final class MQConstants {

    private MQConstants() {
    }

    /**
     * 服务名称前缀
     */
    private static final String SERVICE_NAME_PREFIX = "note.";

    // ---------------------- 本地笔记缓存失效消息 ----------------------
    /**
     * 本地笔记缓存失效交换机
     */
    public static final String NOTE_LOCAL_CACHE_INVALIDATE_EXCHANGE =
            SERVICE_NAME_PREFIX + "local.cache.delete.direct";

    /**
     * 本地笔记缓存失效路由键
     */
    public static final String DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY =
            SERVICE_NAME_PREFIX + "local.cache.delete";

    // ---------------------- Redis 笔记缓存失效消息 ----------------------
    /**
     * Redis 笔记缓存失效交换机
     */
    public static final String NOTE_REDIS_CACHE_INVALIDATE_EXCHANGE =
            SERVICE_NAME_PREFIX + "redis.cache.delete.direct";

    /**
     * Redis 笔记缓存失效队列
     */
    public static final String NOTE_REDIS_CACHE_INVALIDATE_QUEUE =
            SERVICE_NAME_PREFIX + "redis.cache.delete.queue";

    /**
     * Redis 笔记缓存失效路由键
     */
    public static final String DELETE_NOTE_REDIS_CACHE_ROUTING_KEY =
            SERVICE_NAME_PREFIX + "redis.cache.delete";

    // ---------------------- 定义专门的队列和交换机，用于接收处理失败的消息 ----------------------
    /**
     * 接收失败消息交换机
     */
    public static final String ERROR_MESSAGE_EXCHANGE = SERVICE_NAME_PREFIX + "error.direct";

    /**
     * 接收失败消息队列
     */
    public static final String ERROR_MESSAGE_QUEUE = SERVICE_NAME_PREFIX + "error.queue";

    /**
     * 接收失败消息路由键
     */
    public static final String ERROR_MESSAGE_ROUTING_KEY = "error";
}
