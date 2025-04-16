package com.example.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import com.example.model.Product;
import com.example.repository.ProductRepository;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    public static final String CACHE_NAME = "products"; 

    private final ProductRepository productRepository;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        log.info("Fetching all products from database");
        return productRepository.findAll();
    }

    // Cache the result of findById. Key is the productId.
    // If a product with this ID is found in the cache, return it directly.
    // Otherwise, execute the method, cache the result, and return it.
    @Cacheable(value = CACHE_NAME, key = "#id")
    public Optional<Product> getProductById(String id) {
        log.info("Fetching product with id {} from database", id);
        return productRepository.findById(id);
    }

    public List<Product> getProductsByCategory(String category) {
        log.info("Fetching products with category {} from database", category);
        return productRepository.findByCategory(category);
    }

    // Create a new product. Evict all entries in the cache since a new product
    // might affect queries like getAllProducts or findByCategory.
    // A more specific eviction is possible but 'allEntries = true' is simpler here.
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getName());
        return productRepository.save(product);
    }

    // Update an existing product.
    // @CachePut updates the cache with the returned value using the product ID as key.
    // We also need to evict potentially stale list caches (like findByCategory or findAll if cached).
    // Using @Caching allows combining multiple cache operations.
    @Caching(
            put = { @CachePut(value = CACHE_NAME, key = "#result.id") }, // Update cache for this specific product ID
            evict = { @CacheEvict(value = CACHE_NAME, key = "'all'", beforeInvocation = true) } // Example: Evict a potential 'all' key if you cached findAll()
            // Consider more targeted eviction for category cache if needed
    )
    public Optional<Product> updateProduct(String id, Product productDetails) {
        log.info("Attempting to update product with id: {}", id);
        return productRepository.findById(id)
                .map(existingProduct -> {
                    existingProduct.setName(productDetails.getName());
                    existingProduct.setPrice(productDetails.getPrice());
                    existingProduct.setCategory(productDetails.getCategory());
                    Product updatedProduct = productRepository.save(existingProduct);
                    log.info("Successfully updated product with id: {}", updatedProduct.getId());
                    return updatedProduct;
                });
    }

    // Delete a product. Evict the specific product entry and any potentially affected list caches.
    @Caching(evict = {
            @CacheEvict(value = CACHE_NAME, key = "#id"), // Evict the specific product entry
            @CacheEvict(value = CACHE_NAME, key = "'all'", beforeInvocation = true) // Evict potential 'all' key
            // Consider more targeted eviction for category cache if needed
    })
    public boolean deleteProduct(String id) {
        log.info("Attempting to delete product with id: {}", id);
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            log.info("Successfully deleted product with id: {}", id);
            return true;
        } else {
            log.warn("Product with id {} not found for deletion.", id);
            return false;
        }
    }
}
