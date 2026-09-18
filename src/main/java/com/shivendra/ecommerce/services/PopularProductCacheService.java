package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Read-through Redis cache for high-demand products ("iphone", "jordan", "rolex" ...).
 *
 * Layout in Redis:
 *   HASH  popular_products            productId -> Product JSON   (the data)
 *   SET   popular_products:ids        productId                   (fast membership / enumeration)
 *
 * Reads do SMEMBERS + HMGET: two O(k) commands for k popular items, no DB round trip.
 * Writes (create/update/delete in the catalogue) keep the cache coherent immediately rather
 * than waiting for TTL expiry. If Redis is unavailable the service logs and falls back to the
 * database, so the cache is an accelerator, never a single point of failure.
 */
@Service
public class PopularProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(PopularProductCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final String hashKey;
    private final String idSetKey;
    private final long ttlSeconds;
    private final List<String> keywords;

    public PopularProductCacheService(RedisTemplate<String, Object> redisTemplate,
                                      ProductRepository productRepository,
                                      @Value("${cache.popular.key:popular_products}") String hashKey,
                                      @Value("${cache.popular.ttl-seconds:600}") long ttlSeconds,
                                      @Value("${cache.popular.keywords:iphone,jordan,rolex}") String keywordsCsv) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.hashKey = hashKey;
        this.idSetKey = hashKey + ":ids";
        this.ttlSeconds = ttlSeconds;
        this.keywords = List.of(keywordsCsv.toLowerCase().split("\\s*,\\s*"));
    }

    /** Returns popular products from Redis; warms the cache from the DB on a miss. */
    public List<Product> getPopularProducts() {
        try {
            Set<Object> ids = redisTemplate.opsForSet().members(idSetKey);
            if (ids != null && !ids.isEmpty()) {
                List<Object> hashKeys = new ArrayList<>();
                ids.forEach(id -> hashKeys.add(id.toString()));
                List<Object> cached = redisTemplate.opsForHash().multiGet(hashKey, hashKeys);
                List<Product> products = new ArrayList<>();
                for (Object o : cached) {
                    if (o instanceof Product p) {
                        products.add(p);
                    }
                }
                if (!products.isEmpty()) {
                    log.debug("Popular products served from Redis ({} items)", products.size());
                    return products;
                }
            }
            List<Product> fromDb = loadPopularFromDb();
            warm(fromDb);
            return fromDb;
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, serving popular products from DB: {}", e.getMessage());
            return loadPopularFromDb();
        }
    }

    public boolean isPopular(Product product) {
        if (product.getTitle() == null) {
            return false;
        }
        String title = product.getTitle().toLowerCase();
        return keywords.stream().anyMatch(title::contains);
    }

    /** Called after create/update so the cache never serves a stale price or title. */
    public void onProductChanged(Product product) {
        try {
            String id = String.valueOf(product.getId());
            if (isPopular(product) && !product.isDeleted()) {
                redisTemplate.opsForHash().put(hashKey, id, product);
                redisTemplate.opsForSet().add(idSetKey, id);
                refreshTtl();
            } else {
                evict(id);
            }
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, skipping cache update for product {}: {}", product.getId(), e.getMessage());
        }
    }

    public void onProductRemoved(Product product) {
        try {
            evict(String.valueOf(product.getId()));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, skipping cache eviction for product {}: {}", product.getId(), e.getMessage());
        }
    }

    // ---- internals ----

    private List<Product> loadPopularFromDb() {
        Set<Product> result = new LinkedHashSet<>();
        for (String keyword : keywords) {
            result.addAll(productRepository.findByTitleKeyword(keyword));
        }
        return new ArrayList<>(result);
    }

    private void warm(List<Product> products) {
        for (Product p : products) {
            String id = String.valueOf(p.getId());
            redisTemplate.opsForHash().put(hashKey, id, p);
            redisTemplate.opsForSet().add(idSetKey, id);
        }
        if (!products.isEmpty()) {
            refreshTtl();
        }
    }

    private void evict(String id) {
        redisTemplate.opsForHash().delete(hashKey, id);
        redisTemplate.opsForSet().remove(idSetKey, id);
    }

    private void refreshTtl() {
        redisTemplate.expire(hashKey, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(idSetKey, ttlSeconds, TimeUnit.SECONDS);
    }
}
