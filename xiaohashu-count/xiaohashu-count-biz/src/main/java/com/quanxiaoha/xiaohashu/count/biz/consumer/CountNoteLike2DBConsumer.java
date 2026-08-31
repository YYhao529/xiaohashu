package com.quanxiaoha.xiaohashu.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.count.biz.domain.mapper.NoteCountDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 计数：笔记点赞数落库
 */
@Component
@Slf4j
public class CountNoteLike2DBConsumer {

    @Autowired
    private NoteCountDOMapper noteCountDOMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COUNT_LIKE_NOTE_TO_DB_QUEUE)
    public void consume(String message) {
        // 从令牌桶中获取令牌
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 笔记点赞数入库】, {}...", message);

        Map<Long, Integer> countMap = null;
        try {
            countMap = JsonUtils.parseMap(message, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countMap)) {
            // 判断数据库中 t_note_count 表，若笔记计数记录不存在，则插入；若记录已存在，则直接更新
            countMap.forEach((noteId, count) -> {
                noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(count, noteId);
            });
        }
    }

}
