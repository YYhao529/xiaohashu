package com.quanxiaoha.xiaohashu.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.mq.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteLikeDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteLikeDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.enums.LikeUnlikeNoteTypeEnum;
import com.quanxiaoha.xiaohashu.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 笔记点赞、取消点赞 MQ 消费者
 */
@Component
@Slf4j
public class LikeUnlikeNoteConsumer {

    @Autowired
    private NoteLikeDOMapper noteLikeMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.LIKE_UNLIKE_NOTE_TO_DB_QUEUE)
    public void consume(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 判空
        if (Objects.isNull(likeUnlikeNoteMqDTO)) {
            log.error("笔记点赞、取消点赞 MQ 消息为空");
        }

        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}", likeUnlikeNoteMqDTO);

        // 消息类型
        Integer type = likeUnlikeNoteMqDTO.getType();

        // 判断消息类型
        if (Objects.equals(type, LikeUnlikeNoteTypeEnum.LIKE.getCode())) {
            // 点赞笔记
            handleLikeNoteMessage(likeUnlikeNoteMqDTO);
        } else if (Objects.equals(type, LikeUnlikeNoteTypeEnum.UNLIKE.getCode())) {
            // 取消点赞笔记
            handleUnlikeNoteMessage(likeUnlikeNoteMqDTO);
        } else {
            log.error("笔记点赞、取消点赞 MQ 消息类型错误");
        }
    }

    /**
     * 笔记点赞
     */
    private void handleLikeNoteMessage(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 构造 NoteLikeDO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(likeUnlikeNoteMqDTO.getUserId())
                .noteId(likeUnlikeNoteMqDTO.getNoteId())
                .createTime(likeUnlikeNoteMqDTO.getCreateTime())
                .status(likeUnlikeNoteMqDTO.getType())
                .build();

        // 添加或更新笔记点赞记录
        int count = noteLikeMapper.insertOrUpdate(noteLikeDO);

        // TODO：发送计数 MQ
    }

    /**
     * 笔记取消点赞
     */
    private void handleUnlikeNoteMessage(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {

    }
}
