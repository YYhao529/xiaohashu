package com.quanxiaoha.xiaohashu.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.CountCollectUnCollectNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 计数：笔记收藏数
 */
@Component
@Slf4j
public class CountNoteCollectConsumer {

    private final BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000)  // 缓存队列的最大容量
            .batchSize(1000)    // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2))  // 多久聚合一次
            .setConsumerEx(this::consumeMessage)  // 设置消费者方法
            .build();

    @RabbitListener(queues = MQConstants.COUNT_COLLECT_NOTE_QUEUE)
    public void consume(String message) {
        // 往缓存队列中添加数据
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys){
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        List<CountCollectUnCollectNoteMqDTO> list = bodys.stream()
                .map(jsonStr -> JsonUtils.parseObject(jsonStr, CountCollectUnCollectNoteMqDTO.class))
                .toList();

    }
}
