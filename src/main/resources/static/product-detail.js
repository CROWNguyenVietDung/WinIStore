(() => {
  const API_DETAIL = (id) => `/api/public/products/detail/${id}`;

  const { syncCartCountBadge } = window.WinIStore;

  function fmtVnd(n) {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(Number(n || 0));
    } catch {
      return `${n || 0} đ`;
    }
  }

  function escapeHtml(s) {
    if (s == null || s === "") return "";
    const d = document.createElement("div");
    d.textContent = String(s);
    return d.innerHTML;
  }

  function unitPrice(p) {
    const price = Number(p?.price || 0);
    const d = Number(p?.discountPercent || 0);
    if (d > 0 && d <= 100) return Math.round(price * (100 - d) / 100);
    return price;
  }

  function getCart() {
    try {
      return JSON.parse(localStorage.getItem("winstore_cart") || "[]");
    } catch {
      return [];
    }
  }

  function setCart(items) {
    localStorage.setItem("winstore_cart", JSON.stringify(items || []));
  }

  /** Số nguyên ≥ 1; nếu có maxStock thì không vượt quá tồn kho. */
  function clampQty(raw, maxStock) {
    let q = Math.floor(Number(String(raw).replace(",", ".")));
    if (!Number.isFinite(q) || q < 1) q = 1;
    if (maxStock != null && maxStock >= 0) q = Math.min(q, maxStock);
    return q;
  }

  function addToCart(productId, qty = 1) {
    const id = Number(productId);
    if (!Number.isFinite(id)) return;
    const cart = getCart();
    const existing = cart.find((x) => x.productId === id);
    if (existing) existing.quantity = (existing.quantity || 0) + qty;
    else cart.push({ productId: id, quantity: qty });
    setCart(cart);
    syncCartCountBadge?.();
    const el = document.getElementById("cartToast");
    if (el && typeof bootstrap !== "undefined") {
      bootstrap.Toast.getOrCreateInstance(el, { delay: 1400 }).show();
    }
  }

  function render(p) {
    const mount = document.getElementById("detailMount");
    if (!mount) return;

    const img = p?.imageUrl || "https://placehold.co/600x400/png?text=WinIStore";
    const priceNumber = p?.price != null ? Number(p.price) : null;
    const discount = Number(p?.discountPercent || 0);
    const hasDiscount = discount > 0 && discount <= 100 && priceNumber != null;
    const salePrice = hasDiscount ? unitPrice(p) : priceNumber;
    const priceBlock = hasDiscount
      ? `<div class="mb-3">
           <div class="price-original-strike">${fmtVnd(priceNumber)}</div>
           <div class="d-flex align-items-center gap-2 flex-wrap">
             <span class="fs-3 fw-bold price-sale">${fmtVnd(salePrice)}</span>
             <span class="discount-badge">-${discount}%</span>
           </div>
         </div>`
      : `<div class="fs-3 fw-bold price-sale mb-3">${priceNumber != null ? fmtVnd(priceNumber) : "—"}</div>`;

    const stock = p?.stockQuantity != null ? p.stockQuantity : null;
    const stockLine =
      stock == null
        ? ""
        : stock > 0
          ? `<span class="badge text-bg-success">Còn ${stock} sản phẩm</span>`
          : `<span class="badge text-bg-secondary">Hết hàng</span>`;

    const sold = p?.soldQuantity != null ? p.soldQuantity : 0;
    const desc = (p?.description || "").trim();
    const descBlock = desc
      ? `<div class="mt-4">
           <h2 class="h6 fw-bold">Mô tả sản phẩm</h2>
           <div class="text-muted detail-description" style="white-space: pre-wrap;">${escapeHtml(desc)}</div>
         </div>`
      : `<div class="mt-4 text-muted small">Chưa có mô tả chi tiết.</div>`;

    document.title = `${p?.name || "Sản phẩm"} | WinIStore`;

    mount.innerHTML = `
      <div class="row g-4 align-items-start">
        <div class="col-lg-5">
          <div class="bg-white border rounded-3 p-3 shadow-sm text-center">
            <img class="detail-hero-img img-fluid rounded-2" src="${String(img).replace(/"/g, "&quot;")}" alt="${escapeHtml(p?.name || "Sản phẩm")}">
          </div>
        </div>
        <div class="col-lg-7">
          <h1 class="h3 fw-bold mb-3">${escapeHtml(p?.name || "—")}</h1>
          ${priceBlock}
          <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
            ${stockLine}
            <span class="sold-badge">Đã bán ${sold}</span>
          </div>
          <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
            <div>
              <label class="form-label small mb-1" for="detailQty">Số lượng</label>
              <input type="number" id="detailQty" class="form-control" inputmode="numeric"
                     style="width: 110px;" min="1" ${stock != null && stock >= 0 ? `max="${stock}"` : ""}
                     value="1" ${stock === 0 ? "disabled" : ""} />
            </div>
            <button type="button" class="btn btn-success fw-bold" id="detailAddCart" ${stock === 0 ? "disabled" : ""}>
              <i class="bi bi-cart-plus"></i> Thêm vào giỏ hàng
            </button>
          </div>
          ${descBlock}
        </div>
      </div>
    `;

    const qtyInput = document.getElementById("detailQty");
    const maxStock = stock != null && stock >= 0 ? stock : null;

    function syncQtyField() {
      if (!qtyInput) return 1;
      const q = clampQty(qtyInput.value, maxStock);
      qtyInput.value = String(q);
      return q;
    }

    qtyInput?.addEventListener("blur", () => {
      syncQtyField();
    });
    qtyInput?.addEventListener("change", () => {
      syncQtyField();
    });

    document.getElementById("detailAddCart")?.addEventListener("click", () => {
      const btn = document.getElementById("detailAddCart");
      const q = syncQtyField();
      addToCart(p.id, q);
      btn?.classList.add("disabled");
      window.setTimeout(() => btn?.classList.remove("disabled"), 400);
    });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(window.location.search || "");
    const id = Number(params.get("id"));
    const alertEl = document.getElementById("detailAlert");
    const mount = document.getElementById("detailMount");

    function showAlert(msg) {
      if (!alertEl) return;
      alertEl.textContent = msg || "";
      alertEl.classList.toggle("d-none", !msg);
    }

    if (!Number.isFinite(id) || id <= 0) {
      showAlert("Thiếu mã sản phẩm. Quay lại trang chủ và chọn sản phẩm.");
      if (mount) {
        mount.innerHTML = `<p class="text-muted"><a href="./user.html">← Về trang chủ</a></p>`;
      }
      return;
    }

    try {
      const res = await fetch(API_DETAIL(id), { method: "GET" });
      const ct = res.headers.get("content-type") || "";
      const data = ct.includes("application/json") ? await res.json() : null;
      if (!res.ok) {
        throw new Error((data && data.message) || (res.status === 404 ? "Không tìm thấy sản phẩm." : `HTTP ${res.status}`));
      }
      showAlert("");
      render(data);
    } catch (e) {
      showAlert(e?.message || "Không tải được sản phẩm.");
      if (mount) {
        mount.innerHTML = `<p class="text-muted"><a href="./user.html">← Về trang chủ</a></p>`;
      }
    }
  });
})();
