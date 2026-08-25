package com.quanxiaoha.xiaohashu.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FansDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.dataobject.FollowingDO;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FansDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.FollowUserMqDTO;
import com.quanxiaoha.xiaohashu.user.relation.biz.model.dto.UnfollowUserMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 关注、取关 MQ 消费者
 */
@Component
@Slf4j
public class FollowUnfollowConsumer {

    @Autowired
    private FollowingDOMapper followingDOMapper;
    @Autowired
    private FansDOMapper fansDOMapper;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 关注用户消费者
     */
    @RabbitListener(queues = MQConstants.FOLLOW_MESSAGE_QUEUE)
    public void follow(FollowUserMqDTO followUserMqDTO) {
        // 流量削峰：通过获取令牌，执行后续代码，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 判空
        if (Objects.isNull(followUserMqDTO)) {
            log.error("## 拒绝消费：FollowUserMqDTO 为空，转入错误队列");
            throw new AmqpRejectAndDontRequeueException("FollowUserMqDTO 为空，无法提取用户 ID 和被关注用户 ID！");
        }

        // 幂等性：通过联合唯一索引保证
        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();

        log.info("## 即将同步用户关注关系到数据库, 关注用户 ID: {}, 被关注用户 ID: {}", userId, followUserId);

        // 编程式事务：数据库操作，任何异常都会回滚并抛出
        transactionTemplate.executeWithoutResult(status -> {
            followingDOMapper.insertIgnore(FollowingDO.builder()
                    .userId(userId)
                    .followingUserId(followUserId)
                    .createTime(createTime)
                    .build());

            fansDOMapper.insertIgnore(FansDO.builder()
                    .userId(followUserId)
                    .fansUserId(userId)
                    .createTime(createTime)
                    .build());
        });

        log.info("## 成功同步用户关注关系到数据库, 用户 ID: {}, 被关注用户 ID: {}", userId, followUserId);

        // 事务提交成功后才执行 Redis 更新，若 Redis 失败会抛异常，消息重试
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_update_fans_zset.lua")));
        script.setResultType(Long.class);

        // 构建被关注用户的粉丝列表 Redis Key
        String key = RedisKeyConstants.buildUserFansKey(followUserId);
        // 时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(createTime);
        // 执行 Lua 脚本
        redisTemplate.execute(script, List.of(key), userId, timestamp);
    }

    /**
     * 取关用户消费者
     */
    @RabbitListener(queues = MQConstants.UNFOLLOW_MESSAGE_QUEUE)
    public void unfollow(UnfollowUserMqDTO unfollowUserMqDTO) {
        // 流量削峰：通过获取令牌，执行后续代码，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 判空
        if (Objects.isNull(unfollowUserMqDTO)) {
            log.error("## 拒绝消费：UnfollowUserMqDTO 为空，转入错误队列");
            throw new AmqpRejectAndDontRequeueException("UnfollowUserMqDTO 为空，无法提取用户 ID 和被取关用户 ID！");
        }

        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();

        // 删除关注表和粉丝表的关注关系，删除具有天然幂等性，不需要校验幂等性
        // 编程式事务：数据库操作，任何异常都会回滚并抛出
        transactionTemplate.executeWithoutResult(status -> {
            followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
            fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
        });

        log.info("## 成功同步用户取关关系到数据库, 用户 ID: {}, 被取关用户 ID: {}", userId, unfollowUserId);

        // 若数据库删除成功，更新 Redis，将自己从被取关用户的 ZSet 粉丝列表删除
        // 构造被取关用户的粉丝列表 Redis Key
        String unfollowRedisKey = RedisKeyConstants.buildUserFansKey(unfollowUserId);
        // 删除指定粉丝
        redisTemplate.opsForZSet().remove(unfollowRedisKey, userId);
    }
}
