package com.unispeaking.infrastructure.persistence.evaluation.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 评分模块的 MyBatis-Plus Mapper 扫描边界。
 *
 * <p>扫描范围仅覆盖评分持久化包，避免修改应用启动类或将其他模块误纳入
 * 数据库映射。测试环境使用 Mock Mapper，不创建真实数据库连接。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@MapperScan("com.unispeaking.infrastructure.persistence.evaluation.mapper")
public class EvaluationPersistenceConfiguration {
}
