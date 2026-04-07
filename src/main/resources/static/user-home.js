(() => {
  const PAGE_SIZE = 8;
  const API_HOME = "/api/public/products/home";
  const API_BY_CATEGORY = "/api/public/products/by-category";
  const API_BY_TYPE = "/api/public/products/by-type";
  const API_PROMOTIONS = "/api/public/products/promotions";
  const API_SEARCH = "/api/public/products/search";
  const API_REVIEW_SUMMARY = "/api/public/products/review-summary";
  let reviewSummaryMap = new Map();

  const els = {
    homeFilterCard: () => document.getElementById("homeFilterCard"),
    filterType: () => document.getElementById("filterType"),
    minPrice: () => document.getElementById("minPrice"),
    maxPrice: () => document.getElementById("maxPrice"),
    applyBtn: () => document.getElementById("applyFilterBtn"),
    clearBtn: () => document.getElementById("clearFilterBtn"),
    alert: () => document.getElementById("homeAlert"),
    phonesGrid: () => document.getElementById("phonesGrid"),
    accessoriesGrid: () => document.getElementById("accessoriesGrid"),
    usedGrid: () => document.getElementById("usedGrid"),
    sectionPhones: () => document.getElementById("sectionPhones"),
    sectionAccessories: () => document.getElementById("sectionAccessories"),
    sectionUsed: () => document.getElementById("sectionUsed"),
    phonesLoadMoreWrap: () => document.getElementById("phonesLoadMoreWrap"),
    accessoriesLoadMoreWrap: () => document.getElementById("accessoriesLoadMoreWrap"),
    usedLoadMoreWrap: () => document.getElementById("usedLoadMoreWrap"),
    phonesLoadMoreBtn: () => document.getElementById("phonesLoadMoreBtn"),
    accessoriesLoadMoreBtn: () => document.getElementById("accessoriesLoadMoreBtn"),
    usedLoadMoreBtn: () => document.getElementById("usedLoadMoreBtn"),
  };

  const sectionState = {
    phones: { list: [], visibleCount: 0 },
    accessories: { list: [], visibleCount: 0 },
    used: { list: [], visibleCount: 0 },
  };

  const fmtVnd = (n) => {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(n || 0);
    } catch {
      return `${n || 0} đ`;
    }
  };

  function parseNumber(value) {
    if (value == null) return null;
    const s = String(value).trim();
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
  }

  function buildQuery() {
    const type = els.filterType()?.value || "";
    const min = parseNumber(els.minPrice()?.value);
    const max = parseNumber(els.maxPrice()?.value);

    const params = new URLSearchParams();
    if (type) params.set("type", type);
    if (min != null) params.set("minPrice", String(Math.max(0, Math.floor(min))));
    if (max != null) params.set("maxPrice", String(Math.max(0, Math.floor(max))));
    params.set("limit", "60");
    return params.toString();
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function getModeFromUrl() {
    const sp = new URLSearchParams(window.location.search || "");
    const categoryName = (sp.get("categoryName") || "").trim();
    const type = (sp.get("type") || "").trim().toUpperCase();
    const promotions = (sp.get("promotions") || "").trim();
    const searchQ = (sp.get("search") || "").trim();
    const isSearchMode = !!searchQ;
    const isCategoryMode = !isSearchMode && !!categoryName;
    const isTypeMode = !isSearchMode && !isCategoryMode && !!type;
    const isPromotionsMode =
      !isSearchMode && !isCategoryMode && !isTypeMode && (promotions === "1" || promotions.toLowerCase() === "true");
    return {
      categoryName,
      type,
      searchQ,
      isSearchMode,
      isCategoryMode,
      isTypeMode,
      isPromotionsMode,
    };
  }

  function showAlert(msg) {
    const a = els.alert();
    if (!a) return;
    a.textContent = msg || "";
    a.classList.toggle("d-none", !msg);
  }

  function cardHtml(p) {
    const img = p?.imageUrl || "https://placehold.co/600x400/png?text=WinIStore";
    const priceNumber = p?.price != null ? Number(p.price) : null;
    const discount = Number(p?.discountPercent || 0);
    const hasDiscount = discount > 0 && discount <= 100 && priceNumber != null;
    const discountedNumber = hasDiscount ? Math.round(priceNumber * (100 - discount) / 100) : priceNumber;
    const badgeHtml = hasDiscount ? `<span class="discount-badge">-${discount}%</span>` : "";
    const priceHtml = hasDiscount
      ? `<div class="d-flex flex-column">
           <div class="price-original-strike">${fmtVnd(priceNumber)}</div>
           <div class="d-flex align-items-center gap-2">
             <div class="fw-bold price-sale">${fmtVnd(discountedNumber)}</div>
             ${badgeHtml}
           </div>
         </div>`
      : `<div class="d-flex align-items-center gap-2">
           <div class="fw-bold price-sale">${priceNumber != null ? fmtVnd(priceNumber) : "—"}</div>
         </div>`;
    const stock = p?.stockQuantity != null ? p.stockQuantity : null;
    const sold = p?.soldQuantity != null ? p.soldQuantity : 0;
    const stockBadge =
      stock == null
        ? ""
        : stock > 0
          ? `<span class="badge text-bg-success">Còn ${stock}</span>`
          : `<span class="badge text-bg-secondary">Hết hàng</span>`;

    const pid = p?.id != null ? String(p.id) : "";
    const summary = reviewSummaryMap.get(Number(pid)) || { avgRating: 5, reviewCount: 0 };
    const avg = Number(summary.avgRating || 5).toFixed(1);
    const reviewCount = Number(summary.reviewCount || 0);
    const safeName = escapeHtml(p?.name || "—");
    const descriptionHtml = escapeHtml(p?.description || "Mô tả sản phẩm đang được cập nhật.")
      .replace(/\r\n/g, "\n")
      .replace(/\r/g, "\n")
      .replace(/\n/g, "<br>");
    return `
      <div class="col-6 col-md-4 col-lg-3">
        <div class="product-card h-100">
          <button class="cart-fab" type="button" title="Thêm vào giỏ"
                  data-action="addToCart" data-id="${pid}">
            <i class="bi bi-cart-plus"></i>
          </button>
          <a class="text-decoration-none text-dark d-block" href="./product.html?id=${encodeURIComponent(pid)}">
            <img class="product-img" src="${img}" alt="${safeName}">
            <div class="p-3">
              <div class="fw-semibold mb-1 product-name">
                ${safeName}
              </div>
              <div class="product-desc mb-2">
                ${descriptionHtml}
              </div>
              <div class="d-flex align-items-center justify-content-between">
                ${priceHtml}
                ${stockBadge}
              </div>
              <div class="mt-2">
                <span class="sold-badge">Đã bán ${sold}</span>
                <span class="ms-2 small text-warning fw-semibold">★ ${avg}</span>
                <span class="small text-muted">/ ${reviewCount} đánh giá</span>
              </div>
            </div>
          </a>
        </div>
      </div>
    `;
  }

  async function loadReviewSummaryForProducts(list) {
    const ids = (list || []).map((p) => Number(p?.id || 0)).filter((x) => Number.isFinite(x) && x > 0);
    if (!ids.length) return;
    const params = new URLSearchParams();
    ids.forEach((id) => params.append("ids", String(id)));
    try {
      const res = await fetch(`${API_REVIEW_SUMMARY}?${params}`, { method: "GET" });
      const ct = res.headers.get("content-type") || "";
      const data = ct.includes("application/json") ? await res.json() : null;
      if (!res.ok) return;
      const arr = Array.isArray(data) ? data : [];
      arr.forEach((x) => {
        reviewSummaryMap.set(Number(x.productId), {
          avgRating: x.avgRating == null ? 5 : Number(x.avgRating),
          reviewCount: x.reviewCount == null ? 0 : Number(x.reviewCount),
        });
      });
    } catch {
      // ignore
    }
  }

  function getSectionRefs(key) {
    if (key === "accessories") {
      return { gridEl: els.accessoriesGrid(), wrapEl: els.accessoriesLoadMoreWrap() };
    }
    if (key === "used") {
      return { gridEl: els.usedGrid(), wrapEl: els.usedLoadMoreWrap() };
    }
    return { gridEl: els.phonesGrid(), wrapEl: els.phonesLoadMoreWrap() };
  }

  function updateLoadMoreVisibility(key) {
    const state = sectionState[key];
    const { wrapEl } = getSectionRefs(key);
    if (!wrapEl || !state) return;
    const hasMore = state.visibleCount < state.list.length;
    wrapEl.classList.toggle("d-none", !hasMore);
  }

  function renderSection(key, list) {
    const state = sectionState[key];
    const { gridEl } = getSectionRefs(key);
    if (!gridEl || !state) return;
    state.list = Array.isArray(list) ? list : [];
    state.visibleCount = Math.min(PAGE_SIZE, state.list.length);

    if (state.list.length === 0) {
      gridEl.innerHTML = `<div class="col-12"><div class="text-muted">Chưa có sản phẩm.</div></div>`;
      updateLoadMoreVisibility(key);
      return;
    }

    gridEl.innerHTML = state.list.slice(0, state.visibleCount).map(cardHtml).join("");
    updateLoadMoreVisibility(key);
  }

  function loadMoreSection(key) {
    const state = sectionState[key];
    const { gridEl } = getSectionRefs(key);
    if (!gridEl || !state || !state.list.length) return;
    state.visibleCount = Math.min(state.visibleCount + PAGE_SIZE, state.list.length);
    gridEl.innerHTML = state.list.slice(0, state.visibleCount).map(cardHtml).join("");
    updateLoadMoreVisibility(key);
  }

  function setSectionVisible(sectionEl, visible) {
    if (!sectionEl) return;
    sectionEl.classList.toggle("d-none", !visible);
  }

  async function loadHomeProducts() {
    showAlert("");
    const qs = buildQuery();
    const url = qs ? `${API_HOME}?${qs}` : API_HOME;

    const res = await fetch(url, { method: "GET" });
    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : null;
    if (!res.ok) {
      const msg =
        (data && (data.message || data.detail || data.error || data.title)) ||
        `Không tải được sản phẩm (HTTP ${res.status})`;
      throw new Error(msg);
    }
    return data;
  }

  async function loadProductsByCategory(categoryName) {
    showAlert("");
    const params = new URLSearchParams();
    params.set("categoryName", categoryName);
    params.set("limit", "60");

    const res = await fetch(`${API_BY_CATEGORY}?${params}`, { method: "GET" });
    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : null;
    if (!res.ok) {
      const msg =
        (data && (data.message || data.detail || data.error || data.title)) ||
        `Không tải được sản phẩm (HTTP ${res.status})`;
      throw new Error(msg);
    }
    return Array.isArray(data) ? data : [];
  }

  async function loadProductsByType(type) {
    showAlert("");
    const params = new URLSearchParams();
    params.set("type", type);
    params.set("limit", "60");

    const res = await fetch(`${API_BY_TYPE}?${params}`, { method: "GET" });
    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : null;
    if (!res.ok) {
      const msg =
        (data && (data.message || data.detail || data.error || data.title)) ||
        `Không tải được sản phẩm (HTTP ${res.status})`;
      throw new Error(msg);
    }
    return Array.isArray(data) ? data : [];
  }

  async function loadSearchResults(keyword) {
    showAlert("");
    const params = new URLSearchParams();
    params.set("q", keyword);
    params.set("limit", "60");

    const res = await fetch(`${API_SEARCH}?${params}`, { method: "GET" });
    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : null;
    if (!res.ok) {
      const msg =
        (data && (data.message || data.detail || data.error || data.title)) ||
        `Không tìm được (HTTP ${res.status})`;
      throw new Error(msg);
    }
    return Array.isArray(data) ? data : [];
  }

  async function loadPromotions() {
    showAlert("");
    const params = new URLSearchParams();
    params.set("limit", "60");

    const res = await fetch(`${API_PROMOTIONS}?${params}`, { method: "GET" });
    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : null;
    if (!res.ok) {
      const msg =
        (data && (data.message || data.detail || data.error || data.title)) ||
        `Không tải được sản phẩm (HTTP ${res.status})`;
      throw new Error(msg);
    }
    return Array.isArray(data) ? data : [];
  }

  function applyQuickRange(btn) {
    const range = btn?.getAttribute("data-quick-range") || "";
    const [minS, maxS] = range.split("-");
    const min = parseNumber(minS);
    const max = parseNumber(maxS);
    if (els.minPrice()) els.minPrice().value = min != null ? String(min) : "";
    if (els.maxPrice()) els.maxPrice().value = max != null ? String(max) : "";
  }

  function hideFilterInCatalogMode() {
    els.homeFilterCard()?.classList.add("d-none");
  }

  function showFilterInHomeMode() {
    els.homeFilterCard()?.classList.remove("d-none");
  }

  async function refreshHome() {
    try {
      const data = await loadHomeProducts();
      await loadReviewSummaryForProducts([...(data?.phones || []), ...(data?.accessories || []), ...(data?.usedMachines || [])]);

      const type = els.filterType()?.value || "";
      const showPhones = !type || type === "PHONE";
      const showAccessories = !type || type === "ACCESSORY";
      const showUsed = !type || type === "USED";

      setSectionVisible(els.sectionPhones(), showPhones);
      setSectionVisible(els.sectionAccessories(), showAccessories);
      setSectionVisible(els.sectionUsed(), showUsed);

      renderSection("phones", data?.phones || []);
      renderSection("accessories", data?.accessories || []);
      renderSection("used", data?.usedMachines || []);
    } catch (e) {
      showAlert(e?.message || "Có lỗi khi tải sản phẩm.");
      renderSection("phones", []);
      renderSection("accessories", []);
      renderSection("used", []);
    }
  }

  async function refreshCatalog() {
    const mode = getModeFromUrl();
    hideFilterInCatalogMode();

    // Chỉ hiển thị 1 section (dùng sectionPhones làm vùng hiển thị chính)
    setSectionVisible(els.sectionAccessories(), false);
    setSectionVisible(els.sectionUsed(), false);
    setSectionVisible(els.sectionPhones(), true);

    // đổi tiêu đề section theo category/type
    const titleEl = els.sectionPhones()?.querySelector("h4");
    if (titleEl) {
      titleEl.textContent = mode.isPromotionsMode
        ? "Khuyến mãi"
        : mode.isCategoryMode
          ? mode.categoryName
          : (mode.type === "USED" ? "Máy cũ" : mode.type === "ACCESSORY" ? "Phụ kiện" : "Điện thoại");
    }

    try {
      const list = mode.isPromotionsMode
        ? await loadPromotions()
        : mode.isCategoryMode
          ? await loadProductsByCategory(mode.categoryName)
          : await loadProductsByType(mode.type);
      await loadReviewSummaryForProducts(list);

      renderSection("phones", list);
      renderSection("accessories", []);
      renderSection("used", []);
    } catch (e) {
      showAlert(e?.message || "Có lỗi khi tải sản phẩm.");
      renderSection("phones", []);
      renderSection("accessories", []);
      renderSection("used", []);
    }
  }

  async function refreshSearch() {
    const mode = getModeFromUrl();
    hideFilterInCatalogMode();
    setSectionVisible(els.sectionAccessories(), false);
    setSectionVisible(els.sectionUsed(), false);
    setSectionVisible(els.sectionPhones(), true);
    const titleEl = els.sectionPhones()?.querySelector("h4");
    if (titleEl) titleEl.textContent = "Kết quả tìm kiếm";

    try {
      const list = await loadSearchResults(mode.searchQ);
      await loadReviewSummaryForProducts(list);
      renderSection("phones", list);
      renderSection("accessories", []);
      renderSection("used", []);
      if (!list.length) {
        showAlert("Không tìm thấy sản phẩm đang mở bán phù hợp.");
      }
    } catch (e) {
      showAlert(e?.message || "Có lỗi khi tìm kiếm.");
      renderSection("phones", []);
      renderSection("accessories", []);
      renderSection("used", []);
    }
  }

  function wireEvents() {
    els.applyBtn()?.addEventListener("click", refreshHome);
    els.clearBtn()?.addEventListener("click", () => {
      if (els.filterType()) els.filterType().value = "";
      if (els.minPrice()) els.minPrice().value = "";
      if (els.maxPrice()) els.maxPrice().value = "";
      refreshHome();
    });

    document.querySelectorAll("[data-quick-range]").forEach((btn) => {
      btn.addEventListener("click", () => {
        applyQuickRange(btn);
        refreshHome();
      });
    });

    // Enter để lọc
    [els.minPrice(), els.maxPrice()].forEach((el) => {
      el?.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter") refreshHome();
      });
    });
    els.filterType()?.addEventListener("change", refreshHome);
    els.phonesLoadMoreBtn()?.addEventListener("click", () => loadMoreSection("phones"));
    els.accessoriesLoadMoreBtn()?.addEventListener("click", () => loadMoreSection("accessories"));
    els.usedLoadMoreBtn()?.addEventListener("click", () => loadMoreSection("used"));
  }

  function getCart() {
    try {
      return JSON.parse(localStorage.getItem("winstore_cart") || "[]");
    } catch {
      return [];
    }
  }

  function cartCount(cart) {
    return (cart || []).reduce((sum, x) => sum + (Number(x?.quantity) || 0), 0);
  }

  function updateCartBadge() {
    const badge = document.getElementById("cartCountBadge");
    if (!badge) return;
    const count = cartCount(getCart());
    badge.textContent = String(count);
    badge.classList.toggle("d-none", count <= 0);
  }

  function showCartToast() {
    const el = document.getElementById("cartToast");
    if (!el || typeof bootstrap === "undefined") return;
    const t = bootstrap.Toast.getOrCreateInstance(el, { delay: 1400 });
    t.show();
  }

  function animateAddToCartButton(btn) {
    if (!btn) return;
    btn.classList.add("is-adding");
    const icon = btn.querySelector("i");
    const oldClass = icon ? icon.className : "";
    if (icon) icon.className = "bi bi-check2";

    window.setTimeout(() => {
      btn.classList.remove("is-adding");
      if (icon) icon.className = oldClass || "bi bi-cart-plus";
    }, 260);
  }

  function setCart(items) {
    localStorage.setItem("winstore_cart", JSON.stringify(items || []));
  }

  function addToCart(productId, qty = 1) {
    const id = Number(productId);
    if (!Number.isFinite(id)) return;
    const cart = getCart();
    const existing = cart.find((x) => x.productId === id);
    if (existing) existing.quantity = (existing.quantity || 0) + qty;
    else cart.push({ productId: id, quantity: qty });
    setCart(cart);
    updateCartBadge();
    showCartToast();
  }

  document.addEventListener("DOMContentLoaded", () => {
    wireEvents();
    void window.WinIStore?.syncCartCountBadge?.();

    document.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-action='addToCart']");
      if (!btn) return;
      const id = btn.getAttribute("data-id");
      addToCart(id, 1);
      animateAddToCartButton(btn);
    });

    const mode = getModeFromUrl();
    if (mode.isSearchMode) {
      refreshSearch();
    } else if (mode.isCategoryMode || mode.isTypeMode || mode.isPromotionsMode) {
      refreshCatalog();
    } else {
      showFilterInHomeMode();
      refreshHome();
    }
  });
})();

