package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de persistencia para {@link Product}. Sin métodos de filtrado: el
 * contrato de la API no expone catálogo; este repositorio existe para que el
 * seed
 * pueda cargar y recorrer productos al armar el dataset.
 */
public interface ProductRepository {

  Product save(Product product);

  Optional<Product> findById(UUID id);

  List<Product> findAll();
}
