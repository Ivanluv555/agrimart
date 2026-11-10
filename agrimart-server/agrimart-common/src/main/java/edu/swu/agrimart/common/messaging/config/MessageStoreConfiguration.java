package edu.swu.agrimart.common.messaging.config;

import edu.swu.agrimart.common.messaging.service.MessageStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** 显式导入本地消息存储，不启动 broker 连接和定时任务。 */
@Configuration(proxyBeanMethods = false)
public class MessageStoreConfiguration {
    @Bean
    MessageStore messageStore(JdbcTemplate jdbc) { return new MessageStore(jdbc); }
}
