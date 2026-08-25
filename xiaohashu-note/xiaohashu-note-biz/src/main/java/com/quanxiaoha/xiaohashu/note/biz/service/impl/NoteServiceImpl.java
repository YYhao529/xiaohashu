package com.quanxiaoha.xiaohashu.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import com.quanxiaoha.xiaohashu.note.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.TopicDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.enums.NoteStatusEnum;
import com.quanxiaoha.xiaohashu.note.biz.enums.NoteTypeEnum;
import com.quanxiaoha.xiaohashu.note.biz.enums.NoteVisibleEnum;
import com.quanxiaoha.xiaohashu.note.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanxiaoha.xiaohashu.note.biz.rpc.KeyValueRpcService;
import com.quanxiaoha.xiaohashu.note.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteService;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Autowired
    private KeyValueRpcService keyValueRpcService;
    @Autowired
    private TopicDOMapper topicDOMapper;
    @Autowired
    private NoteDOMapper noteDOMapper;
    @Autowired
    private UserRpcService userRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    /**
     * 发布笔记
     */
    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        // 笔记类型
        Integer type = publishNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:    // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                // 检验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 检验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 检验视频是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        // 调用分布式 ID 生成服务，生成笔记 ID
        String noteId = distributedIdGeneratorRpcService.getSnowflakeId();
        // 笔记内容 UUID
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReqVO.getContent();

        // 判断笔记内容是否为空
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容的 UUID
            contentUuid = UUID.randomUUID().toString();
            // RPC: 调用 KV 键值服务，存储短文本
            boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);

            // 若存储失败，抛出业务异常，提示用户发布笔记失败
            if (!isSavedSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }

        // 话题
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 根据话题 ID 去查询话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            // 校验话题是否存在
            if (StringUtils.isBlank(topicName)) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(noteId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(publishNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();

        try {
            // 笔记入库存储
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败", e);

            // RPC: 笔记保存失败，则删除笔记内容
            if (StringUtils.isNotBlank(contentUuid)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
        }

        return Response.success();
    }

    /**
     * 查询笔记详情
     */
    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        // 查询的笔记 ID
        Long noteId = findNoteDetailReqVO.getId();

        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();

        // 先从本地缓存中获取
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspVO);
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailJson)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : noteDetailJson);
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);

            return Response.success(findNoteDetailRspVO);
        }

        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.submit(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long timeToLive = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", timeToLive, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        Integer visible = noteDO.getVisible();
        Long creatorId = noteDO.getCreatorId();
        checkNoteVisible(visible, userId, creatorId);

        // 并发查询优化
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findById(creatorId), threadPoolTaskExecutor);

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    // 获取 Future 返回的结果
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    // 笔记类型
                    Integer type = noteDO.getType();
                    // 图文笔记图片链接(字符串)
                    String imgUriStr = noteDO.getImgUris();
                    // 图文笔记图片链接(集合)
                    List<String> imgUris = null;
                    // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
                    if (Objects.equals(type, NoteTypeEnum.IMAGE_TEXT.getCode())
                            && StringUtils.isNotBlank(imgUriStr)) {
                        imgUris = Arrays.asList(imgUriStr.split(","));
                    }

                    // 构建返参 VO 实体类
                    return FindNoteDetailRspVO.builder()
                            .id(noteId)
                            .type(type)
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(creatorId)
                            .creatorName(findUserByIdRspDTO.getNickname())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(visible)
                            .build();
                });

        // 获取拼装后的 FindNoteDetailRspVO
        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long timeToLive = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            redisTemplate.opsForValue().set(noteDetailRedisKey,
                    JsonUtils.toJsonString(findNoteDetailRspVO), timeToLive, TimeUnit.SECONDS);
        });

        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 笔记更新
     */
    @Override
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        Long noteId = updateNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        NoteDO noteDO = getNoteAndCheckOwner(noteId, userId);

        // 获取笔记的类型
        Integer type = updateNoteReqVO.getType();
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        // 判断笔记类型是否合法
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        /* ------------------------------- 图片链接/视频链接 ------------------------------- */
        // 根据不同的笔记类型，进行不同的校验
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:    // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 检验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 检验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                // 检验视频是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        /* ------------------------------- 话题名称 ------------------------------- */
        // 获取笔记的话题
        Long topicId = updateNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 根据话题 ID 获取话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            // 校验话题是否存在
            if (StringUtils.isBlank(topicName)) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        // 删除 Redis 缓存
        try {
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            redisTemplate.delete(noteDetailRedisKey);
        } catch (Exception e) {
            log.error("删除 Redis 缓存失败, noteId: {}", noteId, e);
        }

        /* ------------------------------- 笔记内容 ------------------------------- */
        // 校验新笔记内容是否为空
        String content = updateNoteReqVO.getContent();
        boolean isContentEmpty = !StringUtils.isNotBlank(content);
        String contentUuid = null;
        if (!isContentEmpty) {
            // 生成新的 UUID
            contentUuid = UUID.randomUUID().toString();
            // 调用 Cassandra 服务存储笔记内容
            boolean saveSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            if (!saveSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_CONTENT_SAVE_FAIL);
            }
        }

        // 构建 NoteDO
        NoteDO newNoteDO = NoteDO.builder()
                .id(noteId)
                .type(type)
                .imgUris(imgUris)
                .videoUri(videoUri)
                .title(updateNoteReqVO.getTitle())
                .topicId(topicId)
                .contentUuid(contentUuid)
                .topicName(topicName)
                .isContentEmpty(isContentEmpty)
                .updateTime(LocalDateTime.now())
                .build();

        try {
            // 更新笔记
            noteDOMapper.updateByPrimaryKey(newNoteDO);
        } catch (Exception e) {
            log.error("==> 笔记更新失败", e);

            // RPC: 笔记保存失败，则删除笔记内容
            if (StringUtils.isNotBlank(content)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        // 异步刷新本地缓存和 Redis 缓存
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 如果原笔记内容不为空，则删除旧内容
                if (!noteDO.getIsContentEmpty()) {
                    keyValueRpcService.deleteNoteContent(noteDO.getContentUuid());
                }
            } catch (Exception e) {
                log.error("## 删除旧笔记内容失败, noteId: {}", noteId, e);
            }
            // 发送缓存失效消息
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 删除笔记
     */
    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        Long noteId = deleteNoteReqVO.getNoteId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        NoteDO noteDO = getNoteAndCheckOwner(noteId, userId);

        // 构建 NoteDO
        NoteDO deletedNoteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 逻辑删除笔记
        noteDOMapper.updateByPrimaryKeySelective(deletedNoteDO);

        threadPoolTaskExecutor.submit(() -> {
            try {
                // 如果笔记内容不为空，则删除笔记内容
                if (!noteDO.getIsContentEmpty()) {
                    keyValueRpcService.deleteNoteContent(noteDO.getContentUuid());
                }
            } catch (Exception e) {
                log.error("## 删除笔记内容失败, noteId: {}", noteId, e);
            }
            // 发送缓存失效消息
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 笔记仅对自己可见
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        getNoteAndCheckOwner(noteId, userId);

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int result = noteDOMapper.updateVisibleOnlyMe(noteDO);

        // 如果影响行数为0，说明该笔记无法修改为仅自己可见
        if (result == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        // 异步向 MQ 发送删除缓存的消息
        threadPoolTaskExecutor.submit(() -> {
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 笔记置顶/取消置顶
     */
    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        Long noteId = topNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        getNoteAndCheckOwner(noteId, userId);

        // 构建置顶/取消置顶 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(topNoteReqVO.getIsTop())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int result = noteDOMapper.updateIsTop(noteDO);

        // 如果影响行数为0，说明该笔记无法置顶/取消置顶
        if (result == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_TOP);
        }

        // 异步向 MQ 发送删除缓存的消息
        threadPoolTaskExecutor.submit(() -> {
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 根据笔记 ID 查询笔记，并校验笔记是否存在且属于当前用户
     */
    private NoteDO getNoteAndCheckOwner(Long noteId, Long userId) {
        // 根据笔记 ID 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(noteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 校验当前登录用户是否是该笔记创作人
        if (!Objects.equals(userId, noteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_PERMISSION_DENIED);
        }
        return noteDO;
    }

    /**
     * 发送本地缓存失效消息
     */
    private void sendLocalCacheInvalidateMessage(Long noteId) {
        try {
            rabbitTemplate.convertAndSend(MQConstants.NOTE_LOCAL_CACHE_INVALIDATE_EXCHANGE,
                    MQConstants.DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY, noteId);
            log.info("==> MQ：本地笔记缓存失效消息发送成功, 笔记 ID: {}", noteId);
        } catch (Exception e) {
            log.error("==> MQ：本地笔记缓存失效消息发送失败, 笔记 ID: {}", noteId, e);
        }
    }

    /**
     * 发送延迟删除 Redis 缓存失效消息
     */
    private void sendRedisCacheInvalidateMessage(Long noteId) {
        try {
            rabbitTemplate.convertAndSend(MQConstants.NOTE_REDIS_CACHE_INVALIDATE_EXCHANGE,
                    MQConstants.DELETE_NOTE_REDIS_CACHE_ROUTING_KEY, noteId, message -> {
                        message.getMessageProperties().setDelay(1000);
                        return message;
                    });
            log.info("==> MQ：延时删除 Redis 笔记缓存消息发送成功, 笔记 ID: {}", noteId);
        } catch (Exception e) {
            log.error("==> MQ：延时删除 Redis 笔记缓存消息发送失败, 笔记 ID: {}", noteId, e);
        }
    }

    /**
     * 校验笔记的可见性
     */
    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) {  // 仅自己可见, 并且访问用户为笔记创建者才能访问，非本人则抛出异常
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    /**
     * 校验笔记的可见性（针对 VO 实体类）
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.nonNull(findNoteDetailRspVO)) {
            Integer visible = findNoteDetailRspVO.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRspVO.getCreatorId());
        }
    }

    /**
     * 删除本地笔记缓存
     */
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }
}
