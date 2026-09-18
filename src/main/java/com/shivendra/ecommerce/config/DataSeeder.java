package com.shivendra.ecommerce.config;

import com.shivendra.ecommerce.dtos.ProductRequestDto;
import com.shivendra.ecommerce.repositories.ProductRepository;
import com.shivendra.ecommerce.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Seeds a small catalogue on an empty database so the API is explorable immediately after
 * {@code ./mvnw spring-boot:run}. Disable with {@code seed.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedCatalogue(ProductRepository productRepository, ProductService productService) {
        return args -> {
            if (productRepository.count() > 0) {
                return;
            }
            List<ProductRequestDto> seed = List.of(
                    product("iPhone 15 Pro", "6.1-inch Super Retina XDR, A17 Pro chip, 256 GB", 134900, 25, "Electronics"),
                    product("MacBook Air M3", "13-inch, 16 GB unified memory, 512 GB SSD", 144900, 10, "Electronics"),
                    product("Sony WH-1000XM5", "Wireless noise-cancelling over-ear headphones", 29990, 40, "Electronics"),
                    product("Air Jordan 1 Retro High", "Chicago colourway, leather upper", 16995, 15, "Footwear"),
                    product("Nike Pegasus 41", "Daily running shoe with ReactX foam", 11895, 60, "Footwear"),
                    product("Rolex Submariner", "Oystersteel, 41 mm, ceramic bezel", 985000, 2, "Watches"),
                    product("Casio G-Shock GA-2100", "Carbon core guard, 200 m water resistance", 9995, 80, "Watches"),
                    product("Clean Code", "Robert C. Martin - a handbook of agile software craftsmanship", 2499, 100, "Books"),
                    product("Designing Data-Intensive Applications", "Martin Kleppmann - the big ideas behind reliable systems", 3299, 70, "Books"),
                    product("Levi's 511 Slim Jeans", "Stretch denim, mid rise", 3499, 120, "Clothing"));
            seed.forEach(productService::createProduct);
            log.info("Seeded {} demo products", seed.size());
        };
    }

    private static ProductRequestDto product(String title, String description, double price, int stock, String category) {
        ProductRequestDto dto = new ProductRequestDto();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setPrice(price);
        dto.setStock(stock);
        dto.setCategory(category);
        dto.setImageUrl("https://picsum.photos/seed/" + title.toLowerCase().replaceAll("[^a-z0-9]", "") + "/400");
        return dto;
    }
}
