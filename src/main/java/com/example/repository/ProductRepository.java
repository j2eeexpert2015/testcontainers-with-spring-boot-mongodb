package com.example.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.model.Product;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByCategory(String category);
}