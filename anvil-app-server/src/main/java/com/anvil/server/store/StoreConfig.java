package com.anvil.server.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储层配置：注册应用所需的内存存储 Bean。
 */
@Configuration
public class StoreConfig {

    /**
     * 注册 {@link InMemoryStore} Bean，供服务层注入使用。
     *
     * @return 内存存储实例
     */
    @Bean
    InMemoryStore inMemoryStore() {
        return new InMemoryStore();
    }
}
