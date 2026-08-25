package com.quanxiaoha.xiaohashu.user.relation.biz.constant;

public final class MQConstants {

    private MQConstants() {
    }

    // ---------------------- 关注消息 ----------------------
    /**
     * 服务名称前缀
     */
    private static final String SERVICE_NAME_PREFIX = "relation.";

    /**
     * 关注消息交换机
     */
    public static final String FOLLOW_MESSAGE_EXCHANGE = SERVICE_NAME_PREFIX + "follow.direct";

    /**
     * 关注消息队列
     */
    public static final String FOLLOW_MESSAGE_QUEUE = SERVICE_NAME_PREFIX + "follow.queue";

    /**
     * 关注消息路由键
     */
    public static final String FOLLOW_MESSAGE_ROUTING_KEY = "follow";

    // ---------------------- 取消关注消息 ----------------------
    /**
     * 取消关注消息交换机
     */
    public static final String UNFOLLOW_MESSAGE_EXCHANGE = SERVICE_NAME_PREFIX + "unfollow.direct";

    /**
     * 取消关注消息队列
     */
    public static final String UNFOLLOW_MESSAGE_QUEUE = SERVICE_NAME_PREFIX + "unfollow.queue";

    /**
     * 取消关注消息路由键
     */
    public static final String UNFOLLOW_MESSAGE_ROUTING_KEY = "unfollow";

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
