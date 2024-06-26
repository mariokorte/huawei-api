package de.mariokorte.huaweiapi.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    @Cacheable(value = "rateLimitFlag", key = "'rateLimit'")
    public boolean isRateLimited() {
        return false; // Default to not rate limited
    }

    @CacheEvict(value = "rateLimitFlag", key = "'rateLimit'")
    public void resetRateLimit() {
        // Method to reset the rate limit flag
    }

    @Cacheable(value = "rateLimitFlag", key = "'rateLimit'")
    public void setRateLimit() {
        // This method is cached with a 5-minute expiry to indicate the rate limit period
    }

    @Cacheable(value = "tokenCache", key = "'token'")
    public String getToken() {
        return null; // Default to no token
    }

    @CacheEvict(value = "tokenCache", key = "'token'")
    public void evictToken() {
        // Method to evict the token from the cache
    }

    @Cacheable(value = "tokenCache", key = "'token'")
    public String cacheToken(String token) {
        return token; // Cache the token
    }
}