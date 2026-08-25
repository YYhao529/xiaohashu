package com.quanxiaoha.xiaohashu.user.relation.biz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.xiaohashu.user.relation.biz.constant.MQConstants;
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

    // ---------------------- 关注消息 ----------------------
    /**
     * 接收关注消息交换机
     */
    @Bean
    public DirectExchange followMessageExchange() {
        return new DirectExchange(MQConstants.FOLLOW_MESSAGE_EXCHANGE);
    }

    /**
     * 接收关注消息队列
     */
    @Bean
    public Queue followMessageQueue() {
        return QueueBuilder
                .durable(MQConstants.FOLLOW_MESSAGE_QUEUE)
                .lazy()
                .build();
    }

    /**
     * 接收关注消息绑定
     */
    @Bean
    public Binding followMessageBinding(Queue followMessageQueue, DirectExchange followMessageExchange) {
        return BindingBuilder.bind(followMessageQueue).to(followMessageExchange)
                .with(MQConstants.FOLLOW_MESSAGE_ROUTING_KEY);
    }

    // ---------------------- 取消关注消息 ----------------------
    @Bean
    public DirectExchange unfollowMessageExchange() {
        return new DirectExchange(MQConstants.UNFOLLOW_MESSAGE_EXCHANGE);
    }

    @Bean
    public Queue unfollowMessageQueue() {
        return QueueBuilder
                .durable(MQConstants.UNFOLLOW_MESSAGE_QUEUE)
                .lazy()
                .build();
    }

    /**
     * 接收取消关注消息绑定
     */
    @Bean
    public Binding unfollowMessageBinding(Queue unfollowMessageQueue, DirectExchange unfollowMessageExchange) {
        return BindingBuilder.bind(unfollowMessageQueue).to(unfollowMessageExchange)
                .with(MQConstants.UNFOLLOW_MESSAGE_ROUTING_KEY);
    }

    // ---------------------- 定义专门的队列和交换机，用于接收处理失败的消息 ----------------------
    /**
     * 接收失败消息交换机
     */
    @Bean
    public DirectExchange errorMessageExchange() {
        return new DirectExchange(MQConstants.ERROR_MESSAGE_EXCHANGE);
    }

    /**
     * 接收失败消息队列
     */
    @Bean
    public Queue errorQueue() {
        return new Queue(MQConstants.ERROR_MESSAGE_QUEUE, true);
    }

    /**
     * 接收失败消息绑定
     */
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
