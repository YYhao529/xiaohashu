package com.quanxiaoha.xiaohashu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.github.phantomthief.collection.BufferTrigger;
import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.NoteCountDOMapper;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.LikeUnlikeNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 计数：笔记点赞数
 */
@Component
@Slf4j
public class CountNoteLikeConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final BufferTrigger<LikeUnlikeNoteMqDTO> bufferTrigger = BufferTrigger.<LikeUnlikeNoteMqDTO>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

    @RabbitListener(queues = MQConstants.COUNT_LIKE_NOTE_QUEUE)
    public void consume(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(likeUnlikeNoteMqDTO);
    }

    private void consumeMessage(List<LikeUnlikeNoteMqDTO> likeUnlikeNoteMqDTOS) {
        if (CollUtil.isEmpty(likeUnlikeNoteMqDTOS)) return;

        // 将点赞、取消点赞消息分开放
        Map<Integer, List<LikeUnlikeNoteMqDTO>> typeMap = likeUnlikeNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(LikeUnlikeNoteMqDTO::getType));

    }
}
