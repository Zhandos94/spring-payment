package kz.javastart.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    public static final String QUEUE_DEPOSIT = "deposit";
    private static final String TOPIC_EXCHANGE_DEPOSIT = "deposit";
    private static final String ROUTING_KEY_DEPOSIT = "deposit";

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Bean
    public TopicExchange depositExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_DEPOSIT);
    }

    @Bean
    public Queue depositQueues() {
        return new Queue(QUEUE_DEPOSIT);
    }

    public Binding depositBinding() {
        return BindingBuilder
                .bind(depositQueues())
                .to(depositExchange())
                .with(ROUTING_KEY_DEPOSIT);
    }
}
