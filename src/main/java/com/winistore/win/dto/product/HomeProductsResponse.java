package com.winistore.win.dto.product;

import java.util.List;

public record HomeProductsResponse(
        List<ProductCardDto> phones,
        List<ProductCardDto> accessories,
        List<ProductCardDto> usedMachines
) {
}

