package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.Buyer;

/** Datos del comprador tal como quedaron registrados en el pedido. */
public record BuyerResponse(String name, String email) {

    public static BuyerResponse from(Buyer buyer) {
        return new BuyerResponse(buyer.name(), buyer.email());
    }
}
