package de.mariokorte.huaweiapi.config;

import com.google.common.cache.CacheBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("meterActivePowerCache", "invActivePowerCache", "rateLimitFlag", "tokenCache") {
            @Override
            @NonNull
            protected Cache createConcurrentMapCache(final String name) {
                if (name.equals("rateLimitFlag")) {
                    return new ConcurrentMapCache(name, CacheBuilder.newBuilder()
                            .expireAfterWrite(5, TimeUnit.MINUTES)
                            .build().asMap(), false);
                }
                if (name.equals("tokenCache")) {
                    return new ConcurrentMapCache(name, CacheBuilder.newBuilder()
                            .expireAfterWrite(8, TimeUnit.HOURS) // Adjust token expiry as needed
                            .build().asMap(), false);
                }
                return new ConcurrentMapCache(name, CacheBuilder.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build().asMap(), false);
            }
        };
    }
}