package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PopularProductCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private SetOperations<String, Object> setOps;
    @Mock
    private ProductRepository productRepository;

    private PopularProductCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new PopularProductCacheService(redisTemplate, productRepository, "popular_products", 600, "iphone,rolex");
    }

    @Test
    void cacheHitSkipsDatabase() {
        Product cached = product(1L, "iPhone 15");
        when(setOps.members("popular_products:ids")).thenReturn(Set.of("1"));
        when(hashOps.multiGet(eq("popular_products"), anyList())).thenReturn(List.of(cached));

        List<Product> result = service.getPopularProducts();

        assertThat(result).containsExactly(cached);
        verify(productRepository, never()).findByTitleKeyword(any());
    }

    @Test
    void cacheMissLoadsFromDbAndWarms() {
        Product fromDb = product(2L, "Rolex Submariner");
        when(setOps.members("popular_products:ids")).thenReturn(Set.of());
        when(productRepository.findByTitleKeyword("iphone")).thenReturn(List.of());
        when(productRepository.findByTitleKeyword("rolex")).thenReturn(List.of(fromDb));

        List<Product> result = service.getPopularProducts();

        assertThat(result).containsExactly(fromDb);
        verify(hashOps).put("popular_products", "2", fromDb);
        verify(setOps).add("popular_products:ids", "2");
        verify(redisTemplate).expire("popular_products", 600, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void redisOutageFallsBackToDb() {
        Product fromDb = product(3L, "iPhone SE");
        when(setOps.members(any())).thenThrow(new RedisConnectionFailureException("down"));
        when(productRepository.findByTitleKeyword("iphone")).thenReturn(List.of(fromDb));
        when(productRepository.findByTitleKeyword("rolex")).thenReturn(List.of());

        assertThat(service.getPopularProducts()).containsExactly(fromDb);
    }

    @Test
    void onProductChangedWritesPopularAndEvictsOthers() {
        Product popular = product(4L, "iPhone 16");
        Product ordinary = product(5L, "Desk lamp");

        service.onProductChanged(popular);
        service.onProductChanged(ordinary);

        verify(hashOps).put("popular_products", "4", popular);
        verify(hashOps).delete("popular_products", "5");
        verify(setOps).remove("popular_products:ids", "5");
    }

    @Test
    void isPopularMatchesKeywordsCaseInsensitively() {
        assertThat(service.isPopular(product(1L, "Apple IPHONE 15"))).isTrue();
        assertThat(service.isPopular(product(1L, "Wooden chair"))).isFalse();
    }

    private static Product product(Long id, String title) {
        Product p = new Product();
        p.setId(id);
        p.setTitle(title);
        p.setPrice(1.0);
        return p;
    }
}
