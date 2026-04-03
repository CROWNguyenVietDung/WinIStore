package com.winistore.win.controller;

import com.winistore.win.dto.product.HomeProductsResponse;
import com.winistore.win.dto.product.ProductCardDto;
import com.winistore.win.model.entity.Product;
import com.winistore.win.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {
    private final ProductRepository productRepository;

    public PublicProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/home")
    public HomeProductsResponse getHomeProducts(
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "12") int limit
    ) {
        BigDecimal min = minPrice != null ? minPrice : BigDecimal.ZERO;
        BigDecimal max = maxPrice != null ? maxPrice : new BigDecimal("999999999999");

        int safeLimit = Math.max(1, Math.min(limit, 60));
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        String normalizedType = (type == null || type.isBlank())
                ? null
                : type.trim().toUpperCase(Locale.ROOT);

        List<ProductCardDto> phones = List.of();
        List<ProductCardDto> accessories = List.of();
        List<ProductCardDto> usedMachines = List.of();

        if (normalizedType == null || "PHONE".equals(normalizedType)) {
            phones = productRepository
                    .findByCategoryTypeIgnoreCaseAndPriceBetween("PHONE", min, max, pageable)
                    .stream()
                    .filter(this::isVisibleForUser)
                    .map(this::toCardDto)
                    .toList();
        }

        if (normalizedType == null || "ACCESSORY".equals(normalizedType)) {
            accessories = productRepository
                    .findByCategoryTypeIgnoreCaseAndPriceBetween("ACCESSORY", min, max, pageable)
                    .stream()
                    .filter(this::isVisibleForUser)
                    .map(this::toCardDto)
                    .toList();
        }

        if (normalizedType == null || "USED".equals(normalizedType)) {
            usedMachines = productRepository
                    .findByCategoryTypeIgnoreCaseAndPriceBetween("USED", min, max, pageable)
                    .stream()
                    .filter(this::isVisibleForUser)
                    .map(this::toCardDto)
                    .toList();
        }

        return new HomeProductsResponse(phones, accessories, usedMachines);
    }

    @GetMapping("/by-category")
    public List<ProductCardDto> getByCategory(
            @RequestParam String categoryName,
            @RequestParam(defaultValue = "12") int limit
    ) {
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 60));
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        return productRepository
                .findByCategoryNameIgnoreCaseAndPriceBetween(
                        categoryName.trim(),
                        BigDecimal.ZERO,
                        new BigDecimal("999999999999"),
                        pageable
                )
                .stream()
                .filter(this::isVisibleForUser)
                .map(this::toCardDto)
                .toList();
    }

    @GetMapping("/by-type")
    public List<ProductCardDto> getByType(
            @RequestParam String type,
            @RequestParam(defaultValue = "12") int limit
    ) {
        if (type == null || type.isBlank()) {
            return List.of();
        }

        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (!"PHONE".equals(normalizedType) && !"ACCESSORY".equals(normalizedType) && !"USED".equals(normalizedType)) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 60));
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        return productRepository
                .findByCategoryTypeIgnoreCaseAndPriceBetween(
                        normalizedType,
                        BigDecimal.ZERO,
                        new BigDecimal("999999999999"),
                        pageable
                )
                .stream()
                .filter(this::isVisibleForUser)
                .map(this::toCardDto)
                .toList();
    }

    private ProductCardDto toCardDto(Product p) {
        var c = p.getCategory();
        return new ProductCardDto(
                p.getId(),
                p.getName(),
                p.getPrice(),
                normalizeDiscountPercent(p.getDiscountPercent()),
                p.getStockQuantity(),
                p.getSoldQuantity() == null ? 0 : p.getSoldQuantity(),
                p.getImageUrl(),
                c != null ? c.getId() : null,
                c != null ? c.getName() : null,
                c != null ? c.getType() : null
        );
    }

    private boolean isVisibleForUser(Product p) {
        // null = dữ liệu cũ, coi như mặc định hiển thị
        if (p == null) return false;
        if (p.getCategory() != null && isRepairCategory(p.getCategory().getType(), p.getCategory().getName())) {
            return false;
        }
        return p.getVisibleForUser() == null || Boolean.TRUE.equals(p.getVisibleForUser());
    }

    @GetMapping("/promotions")
    public List<ProductCardDto> promotions(@RequestParam(defaultValue = "24") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 60));
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        return productRepository
                .findByDiscountPercentGreaterThanAndPriceBetween(0, BigDecimal.ZERO, new BigDecimal("999999999999"), pageable)
                .stream()
                .filter(this::isVisibleForUser)
                .map(this::toCardDto)
                .toList();
    }

    @GetMapping("/by-ids")
    public List<ProductCardDto> byIds(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productRepository.findAllById(ids).stream()
                .filter(this::isVisibleForUser)
                .map(this::toCardDto)
                .toList();
    }

    private int normalizeDiscountPercent(Integer v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private boolean isRepairCategory(String type, String name) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        return "REPAIR".equals(t)
                || "SERVICE".equals(t)
                || n.contains("SỬA CHỮA")
                || n.contains("SUA CHUA")
                || n.contains("DỊCH VỤ")
                || n.contains("DICH VU");
    }
}

