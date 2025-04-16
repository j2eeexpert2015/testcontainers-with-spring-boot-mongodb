package com.example.repository;

import com.example.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
public class ProductRepositoryContainerIT {

    @Container
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:4.4.2"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("[Repo Test] Should find products by category")
    void testFindByCategory() {
        Product p1 = new Product("Chair", 80.0, "Furniture");
        Product p2 = new Product("Table", 150.0, "Furniture");
        Product p3 = new Product("Laptop", 1200.0, "Electronics");

        productRepository.saveAll(List.of(p1, p2, p3));

        List<Product> furniture = productRepository.findByCategory("Furniture");
        assertThat(furniture).hasSize(2);

        List<Product> electronics = productRepository.findByCategory("Electronics");
        assertThat(electronics).hasSize(1);
    }
}
