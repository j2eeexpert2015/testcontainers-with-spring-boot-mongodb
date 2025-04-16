package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

// Relevant imports
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.model.Product;
import com.example.repository.ProductRepository;

@Testcontainers
@SpringBootTest
class ProductServiceContainerIT {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceContainerIT.class);

    @Container
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:4.4.2"));

    // Dynamically set the MongoDB connection URI after the container has started.
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Ensure clean state for each test
        logger.info("Cleaning up Product collection and cache before test.");
        productRepository.deleteAll();
        Cache cache = cacheManager.getCache(ProductService.CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("[TC] Should create a new product successfully")
    void createProductTest() {
        Product newProduct = new Product("Laptop Pro", 1500.00, "Electronics");
        Product savedProduct = productService.createProduct(newProduct);

        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getId()).isNotNull().isNotEmpty();
        assertThat(savedProduct.getName()).isEqualTo("Laptop Pro");
        assertThat(productRepository.findById(savedProduct.getId())).isPresent();
    }

    @Test
    @DisplayName("[TC] Should find product by ID and demonstrate caching")
    void getProductByIdAndCacheTest() {
        Product savedProduct = productRepository.save(new Product("Test Mouse", 25.00, "Accessories"));
        String productId = savedProduct.getId();

        logger.info("--- First call to getProductById for ID: {} ---", productId);
        Optional<Product> productOpt1 = productService.getProductById(productId); // Executes @Cacheable
        logger.info("--- Finished first call ---");

        assertThat(productOpt1).isPresent();
        // Verify cache contains the raw Product
        Cache.ValueWrapper wrapper1 = cacheManager.getCache(ProductService.CACHE_NAME).get(productId);
        assertThat(wrapper1).isNotNull();
        assertThat(wrapper1.get()).isInstanceOf(Product.class);
        logger.info("Product {} found in cache after first call.", productId);


        logger.info("--- Second call to getProductById for ID: {} ---", productId);
        Optional<Product> productOpt2 = productService.getProductById(productId);
        logger.info("--- Finished second call ---");

        assertThat(productOpt2).isPresent();
        logger.info("Verified product {} was likely served from cache on second call.", productId);
    }

    @Test
    @DisplayName("[TC] Should return empty Optional for non-existent product ID")
    void getProductByIdNotFoundTest() {
        String nonExistentId = "nonExistentId123";
        Optional<Product> productOpt = productService.getProductById(nonExistentId); // Executes @Cacheable

        assertThat(productOpt).isNotPresent();

        // Verify cache interaction for null/empty result
        Cache cache = cacheManager.getCache(ProductService.CACHE_NAME);
        assertThat(cache).isNotNull();
        Cache.ValueWrapper wrapper = cache.get(nonExistentId);
        if (wrapper != null) {
             assertThat(wrapper.get()).isNull();
        }
        logger.info("Verified cache does not contain a non-null product for ID: {}", nonExistentId);
    }


    @Test
    @DisplayName("[TC] Should find products by category")
    void getProductsByCategoryTest() {
        productRepository.save(new Product("Keyboard", 75.00, "Accessories"));
        productRepository.save(new Product("Webcam", 120.00, "Accessories"));
        productRepository.save(new Product("Monitor", 300.00, "Displays"));

        List<Product> accessories = productService.getProductsByCategory("Accessories");
        List<Product> displays = productService.getProductsByCategory("Displays");
        List<Product> unknown = productService.getProductsByCategory("Unknown");

        assertThat(accessories).hasSize(2);
        assertThat(displays).hasSize(1);
        assertThat(unknown).isEmpty();
    }

}
