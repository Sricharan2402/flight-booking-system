package com.flightbooking.common.config

import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfiguration {

    @Bean
    fun jedisConnectionFactory(redisProperties: RedisProperties): JedisConnectionFactory {
        val config = JedisPoolConfig().apply {
            maxTotal = redisProperties.jedis.pool.maxActive
            maxIdle = redisProperties.jedis.pool.maxIdle
            minIdle = redisProperties.jedis.pool.minIdle
            maxWaitMillis = redisProperties.jedis.pool.maxWait.toMillis()
        }

        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                password = RedisPassword.of(redisProperties.password)
            }
        }

        val clientConfig = JedisClientConfiguration.builder()
            .usePooling()
            .poolConfig(config)
            .and()
            .readTimeout(redisProperties.timeout)
            .build()

        return JedisConnectionFactory(redisConfig, clientConfig)
    }

    @Bean
    fun redisTemplate(connectionFactory: JedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
            afterPropertiesSet()
        }
    }

    @Bean
    fun stringRedisTemplate(connectionFactory: JedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    @Bean
    fun searchCacheRedisTemplate(connectionFactory: JedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
    }
}