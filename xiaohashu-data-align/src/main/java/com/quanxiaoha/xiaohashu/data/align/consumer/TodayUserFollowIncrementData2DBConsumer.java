package com.quanxiaoha.xiaohashu.data.align.consumer;

import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 日增量数据落库：用户关注、取消关注
 */
@Component
@Slf4j
public class TodayUserFollowIncrementData2DBConsumer {

    @RabbitListener(queues = MQConstants.COUNT_FOLLOWINGS_UNFOLLOW_ALIGN_QUEUE)
    public void consume(String message) {
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", message);
    }
}
