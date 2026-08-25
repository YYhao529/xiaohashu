package com.quanxiaoha.xiaohashu.note.biz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quanxiaoha.xiaohashu.note.biz.constant.MQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter =
                new Jackson2JsonMessageConverter(objectMapper);
        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    // ---------------------- 本地笔记缓存失效消息 ----------------------

    /**
     * 本地笔记缓存失效交换机
     */
    @Bean
    public DirectExchange deleteNoteLocalCacheExchange() {
        return new DirectExchange(MQConstants.NOTE_LOCAL_CACHE_INVALIDATE_EXCHANGE);
    }

    /**
     * 笔记缓存失效临时匿名队列
     */
    @Bean
    public Queue deleteNoteLocalCacheQueue() {
        return new AnonymousQueue();
    }

    /**
     * 删除本地笔记缓存绑定
     */
    @Bean
    public Binding deleteNoteLocalCacheBinding(DirectExchange deleteNoteLocalCacheExchange,
                                               Queue deleteNoteLocalCacheQueue) {
        return BindingBuilder.bind(deleteNoteLocalCacheQueue).to(deleteNoteLocalCacheExchange)
                .with(MQConstants.DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY);
    }

    // ---------------------- Redis 笔记缓存失效消息 ----------------------

    /**
     * Redis 笔记缓存失效交换机
     */
    @Bean
    public DirectExchange deleteNoteRedisCacheExchange() {
        return ExchangeBuilder
                .directExchange(MQConstants.NOTE_REDIS_CACHE_INVALIDATE_EXCHANGE)
                .delayed()
                .durable(true)
                .build();
    }

    /**
     * Redis 笔记缓存失效队列
     */
    @Bean
    public Queue deleteNoteRedisCacheQueue() {
        return QueueBuilder
                .durable(MQConstants.NOTE_REDIS_CACHE_INVALIDATE_QUEUE)
                .lazy()
                .build();
    }

    /**
     * Redis 笔记缓存失效绑定
     */
    @Bean
    public Binding deleteNoteRedisCacheBinding(DirectExchange deleteNoteRedisCacheExchange,
                                               Queue deleteNoteRedisCacheQueue) {
        return BindingBuilder.bind(deleteNoteRedisCacheQueue).to(deleteNoteRedisCacheExchange)
                .with(MQConstants.DELETE_NOTE_REDIS_CACHE_ROUTING_KEY);
    }

    // ---------------------- 定义专门的队列和交换机，用于接收处理失败的消息 ----------------------
    @Bean
    public DirectExchange errorMessageExchange() {
        return new DirectExchange(MQConstants.ERROR_MESSAGE_EXCHANGE);
    }

    @Bean
    public Queue errorQueue() {
        return new Queue(MQConstants.ERROR_MESSAGE_QUEUE, true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange) {
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange)
                .with(MQConstants.ERROR_MESSAGE_ROUTING_KEY);
    }

    // 定义一个 RepublishMessageRecoverer，关联队列和交换机
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, MQConstants.ERROR_MESSAGE_EXCHANGE,
                MQConstants.ERROR_MESSAGE_ROUTING_KEY);
    }
}
