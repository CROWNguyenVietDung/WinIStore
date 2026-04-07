package com.winistore.win.controller;

import com.winistore.win.dto.admin.AdminCategoryResponse;
import com.winistore.win.dto.admin.AdminProductResponse;
import com.winistore.win.dto.admin.ProductVisibilityUpdateRequest;
import com.winistore.win.model.entity.Category;
import com.winistore.win.model.entity.Product;
import com.winistore.win.repository.CategoryRepository;
import com.winistore.win.repository.ProductRepository;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public AdminProductController(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .filter(c -> !isRepairCategory(c))
                .map(c -> new AdminCategoryResponse(c.getId(), c.getName(), c.getType()))
                .toList();
    }

    // Xóa hẳn danh mục/sản phẩm "dịch vụ sửa chữa" khỏi DB
    @DeleteMapping("/purge-repair-categories")
    @Transactional
    public void purgeRepairCategories() {
        List<Category> repairCategories = categoryRepository.findAll().stream()
                .filter(this::isRepairCategory)
                .toList();

        if (repairCategories.isEmpty()) {
            return;
        }

        List<Long> ids = repairCategories.stream().map(Category::getId).toList();
        productRepository.deleteAllByCategoryIdIn(ids);
        categoryRepository.deleteAllByIdInBatch(ids);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminProductResponse> listProducts(@RequestParam(required = false) Long categoryId) {
        List<Product> products = (categoryId == null)
                ? productRepository.findAll()
                : productRepository.findByCategoryId(categoryId);

        return products.stream()
                .filter(p -> p.getCategory() == null || !isRepairCategory(p.getCategory()))
                .map(this::toAdminResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminProductResponse details(@PathVariable Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));
        return toAdminResponse(p);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public AdminProductResponse create(
            @RequestParam String name,
            @RequestParam Long categoryId,
            @RequestParam BigDecimal price,
            @RequestParam(defaultValue = "0") Integer discountPercent,
            @RequestParam Integer stockQuantity,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean visibleForUser,
            @RequestPart(required = true, name = "image") MultipartFile image
    ) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh sản phẩm.");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục"));
        if (isRepairCategory(category)) {
            throw new IllegalArgumentException("Danh mục dịch vụ sửa chữa đã bị loại bỏ.");
        }

        String savedImage = saveImage(image);

        Product p = Product.builder()
                .name(name)
                .category(category)
                .price(price)
                .discountPercent(normalizeDiscountPercent(discountPercent))
                .stockQuantity(stockQuantity)
                .image(savedImage)
                .description(description)
                .visibleForUser(visibleForUser)
                .build();

        Product saved = productRepository.save(p);
        return toAdminResponse(saved);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public AdminProductResponse update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam Long categoryId,
            @RequestParam BigDecimal price,
            @RequestParam(defaultValue = "0") Integer discountPercent,
            @RequestParam Integer stockQuantity,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean visibleForUser,
            @RequestPart(required = false, name = "image") MultipartFile image
    ) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục"));
        if (isRepairCategory(category)) {
            throw new IllegalArgumentException("Danh mục dịch vụ sửa chữa đã bị loại bỏ.");
        }

        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setDiscountPercent(normalizeDiscountPercent(discountPercent));
        p.setStockQuantity(stockQuantity);
        p.setDescription(description);
        p.setVisibleForUser(visibleForUser);

        if (image != null && !image.isEmpty()) {
            p.setImage(saveImage(image));
        }

        Product saved = productRepository.save(p);
        return toAdminResponse(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy sản phẩm");
        }
        productRepository.deleteById(id);
    }

    @PatchMapping(path = "/{id}/visibility", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminProductResponse updateVisibility(
            @PathVariable Long id,
            @RequestBody ProductVisibilityUpdateRequest req
    ) {
        if (req == null || req.visibleForUser() == null) {
            throw new IllegalArgumentException("Trạng thái mở bán không hợp lệ");
        }
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));

        p.setVisibleForUser(req.visibleForUser());
        return toAdminResponse(productRepository.save(p));
    }

    private boolean isVisibleForUser(Product p) {
        // null = giữ nguyên dữ liệu cũ (mặc định coi như hiển thị)
        return p.getVisibleForUser() == null || Boolean.TRUE.equals(p.getVisibleForUser());
    }

    private AdminProductResponse toAdminResponse(Product p) {
        Category c = p.getCategory();
        return new AdminProductResponse(
                p.getId(),
                p.getName(),
                p.getPrice(),
                normalizeDiscountPercent(p.getDiscountPercent()),
                p.getStockQuantity(),
                p.getImage(),
                c != null ? c.getId() : null,
                c != null ? c.getName() : null,
                c != null ? c.getType() : null,
                p.getDescription(),
                isVisibleForUser(p)
        );
    }

    private String saveImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("File không phải ảnh hợp lệ.");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (!ext.matches("\\.(png|jpg|jpeg|gif|webp)")) {
            ext = ".png";
        }

        String filename = UUID.randomUUID() + ext;
        Path productDir = Paths.get(System.getProperty("user.dir"), "uploads", "products");

        try {
            Files.createDirectories(productDir);
            Path target = productDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/products/" + filename;
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể lưu ảnh sản phẩm. Vui lòng thử lại.");
        }
    }

    private boolean isRepairCategory(Category c) {
        if (c == null) return false;
        String type = c.getType() == null ? "" : c.getType().trim().toUpperCase(Locale.ROOT);
        String name = c.getName() == null ? "" : c.getName().trim().toUpperCase(Locale.ROOT);
        return "REPAIR".equals(type)
                || "SERVICE".equals(type)
                || name.contains("SỬA CHỮA")
                || name.contains("SUA CHUA")
                || name.contains("DỊCH VỤ")
                || name.contains("DICH VU");
    }

    private int normalizeDiscountPercent(Integer v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}

