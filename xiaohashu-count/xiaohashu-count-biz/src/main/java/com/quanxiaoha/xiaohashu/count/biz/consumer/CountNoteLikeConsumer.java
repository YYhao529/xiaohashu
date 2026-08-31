package com.quanxiaoha.xiaohashu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.count.biz.enums.LikeUnlikeNoteTypeEnum;
import com.quanxiaoha.xiaohashu.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 计数：笔记点赞数
 */
@Component
@Slf4j
public class CountNoteLikeConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

    @RabbitListener(queues = MQConstants.COUNT_LIKE_NOTE_QUEUE)
    public void consume(String message) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        if (CollUtil.isEmpty(bodys)) return;

        // List<String> 转 List<CountLikeUnlikeNoteMqDTO>
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class)).toList();

        // 按照 noteId 分组
        Map<Long, List<CountLikeUnlikeNoteMqDTO>> map = countLikeUnlikeNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeNoteMqDTO::getNoteId));

        // 定义一个 Map，用于存储每个 noteId 的点赞数
        Map<Long, Integer> countMap = Maps.newHashMap();

        // 遍历每个 noteId 分组
        map.forEach((noteId, likeUnlikeMqDTOS)->{
            // 最终的计数值，默认为 0
            int finalCount=0;
            for (CountLikeUnlikeNoteMqDTO likeUnlikeMqDTO : likeUnlikeMqDTOS) {
                if (Objects.equals(likeUnlikeMqDTO.getType(), LikeUnlikeNoteTypeEnum.LIKE.getCode())) { // 点赞
                    finalCount++;
                } else if (Objects.equals(likeUnlikeMqDTO.getType(), LikeUnlikeNoteTypeEnum.UNLIKE.getCode())){ // 取消点赞
                    finalCount--;
                }
            }
            // 将分组后统计出的最终计数，存入 countMap 中
            countMap.put(noteId, finalCount);
        });

        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        // 更新 Redis 中的点赞数
        countMap.forEach((noteId,count)->{
            // 构建 Redis Key
            String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);
            // 判断 Redis 中 Hash 是否存在
            boolean isExisted = redisTemplate.hasKey(countNoteKey);

            // 若存在才会更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isExisted){
                // 对目标用户 Hash 中的点赞数字段进行计数操作
                redisTemplate.opsForHash().increment(countNoteKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }
        });

        // 异步发送 MQ
        threadPoolTaskExecutor.submit(()->{
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_LIKE_NOTE_TO_DB_EXCHANGE, MQConstants.COUNT_LIKE_NOTE_TO_DB_ROUTING_KEY,
                        JsonUtils.toJsonString(countMap));
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功");
            }catch (Exception e){
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", e);
            }
        });
    }
}
