package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.entity.Seller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de persistencia para {@link Seller}. Igual que
 * {@link ProductRepository},
 * mínimo para que el seed pueda cargar y recorrer vendedores.
 */
public interface SellerRepository {

  Seller save(Seller seller);

  Optional<Seller> findById(UUID id);

  List<Seller> findAll();
}
