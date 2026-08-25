package com.quanxiaoha.xiaohashu.user.relation.biz;

import com.quanxiaoha.xiaohashu.user.relation.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FollowingDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;

@SpringBootTest
@Slf4j
class MQTests {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 测试：发送 1000 条 MQ
     */
    @Test
    void testBatchSendMQ(){
        for (long i = 1; i <= 10000; i++) {
            // 构建消息体 DTO
            FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                    .userId(i)
                    .followUserId(i)
                    .createTime(LocalDateTime.now())
                    .build();

            log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);

            // 发送消息
            threadPoolTaskExecutor.execute(() -> {
                try {
                    rabbitTemplate.convertAndSend(MQConstants.FOLLOW_MESSAGE_EXCHANGE,
                            MQConstants.FOLLOW_MESSAGE_ROUTING_KEY, followUserMqDTO);
                    log.info("==> MQ 发送成功");
                } catch (AmqpException e) {
                    log.error("==> MQ 发送异常: ", e);
                }
            });
        }
    }
}
