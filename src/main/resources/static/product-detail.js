(() => {
  const API_DETAIL = (id) => `/api/public/products/detail/${id}`;
  const API_REVIEWS = (id) => `/api/public/products/${id}/reviews`;
  const API_DELETE_REVIEW = (productId, reviewId, userId) => `/api/public/products/${productId}/reviews/${reviewId}?userId=${encodeURIComponent(userId)}`;

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

  function renderStars(n) {
    const v = Math.max(1, Math.min(5, Number(n || 0)));
    return "★★★★★".slice(0, v) + "☆☆☆☆☆".slice(0, 5 - v);
  }

  async function loadReviews(productId) {
    const host = document.getElementById("reviewList");
    if (!host) return;
    const session = window.WinIStore?.getSession?.();
    const currentUserId = session?.id != null ? Number(session.id) : null;
    try {
      const res = await fetch(API_REVIEWS(productId), { method: "GET" });
      const data = await res.json();
      if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
      const list = Array.isArray(data) ? data : [];
      if (!list.length) {
        host.innerHTML = `<div class="text-muted small">Chưa có đánh giá nào.</div>`;
        return;
      }
      host.innerHTML = list.map((r) => `
        <div class="border rounded p-3 mb-3 bg-white">
          <div class="d-flex justify-content-between gap-2 align-items-start">
            <div class="fw-semibold">${escapeHtml(r.userName || "Khách hàng")}</div>
            <div class="d-flex align-items-center gap-2">
              <div class="fs-5 text-warning">${renderStars(r.rating)}</div>
              ${currentUserId != null && Number(r.userId) === currentUserId ? `<button class="btn btn-sm btn-outline-danger py-0 px-2" data-action="deleteReview" data-review-id="${r.id}">Gỡ</button>` : ""}
            </div>
          </div>
          <div class="text-muted mb-1">${r.createdAt ? new Date(r.createdAt).toLocaleString("vi-VN") : ""}</div>
          <div>${escapeHtml(r.comment || "")}</div>
        </div>
      `).join("");
    } catch (e) {
      host.innerHTML = `<div class="text-danger small">${escapeHtml(e?.message || "Không tải được đánh giá.")}</div>`;
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
      <div class="mt-4">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <div class="d-flex justify-content-between align-items-center gap-2 mb-3">
              <h2 class="h4 mb-0">Đánh giá sản phẩm</h2>
              <button type="button" class="btn btn-outline-primary" id="btnToggleReviewForm">Viết đánh giá</button>
            </div>
            <div id="reviewFormWrap" class="d-none mb-3">
              <div id="reviewAlert" class="alert alert-warning d-none" role="alert"></div>
              <div class="mb-2">
                <label class="form-label fs-5 mb-1">Chấm điểm</label>
                <div id="reviewStarPicker" class="d-flex gap-2 text-warning">
                  <button type="button" class="btn btn-link p-0 text-warning text-decoration-none lh-1" style="font-size:2.2rem;" data-star="1">☆</button>
                  <button type="button" class="btn btn-link p-0 text-warning text-decoration-none lh-1" style="font-size:2.2rem;" data-star="2">☆</button>
                  <button type="button" class="btn btn-link p-0 text-warning text-decoration-none lh-1" style="font-size:2.2rem;" data-star="3">☆</button>
                  <button type="button" class="btn btn-link p-0 text-warning text-decoration-none lh-1" style="font-size:2.2rem;" data-star="4">☆</button>
                  <button type="button" class="btn btn-link p-0 text-warning text-decoration-none lh-1" style="font-size:2.2rem;" data-star="5">☆</button>
                </div>
                <input type="hidden" id="reviewRating" value="5" />
              </div>
              <div class="mb-2">
                <label class="form-label fs-5 mb-1">Bình luận</label>
                <textarea id="reviewComment" class="form-control fs-5" rows="4" maxlength="2000" placeholder="Chia sẻ trải nghiệm của bạn..."></textarea>
              </div>
              <button type="button" class="btn btn-primary" id="btnSubmitReview">Đăng đánh giá</button>
            </div>
            <div id="reviewList"></div>
          </div>
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

    const formWrap = document.getElementById("reviewFormWrap");
    const reviewAlert = document.getElementById("reviewAlert");
    const starPicker = document.getElementById("reviewStarPicker");
    function paintStars(rating) {
      const val = Math.max(1, Math.min(5, Number(rating || 5)));
      document.getElementById("reviewRating").value = String(val);
      starPicker?.querySelectorAll("button[data-star]").forEach((btn) => {
        const s = Number(btn.getAttribute("data-star") || 0);
        btn.textContent = s <= val ? "★" : "☆";
      });
    }
    paintStars(5);
    starPicker?.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-star]");
      if (!btn) return;
      paintStars(Number(btn.getAttribute("data-star")));
    });
    document.getElementById("btnToggleReviewForm")?.addEventListener("click", () => {
      formWrap?.classList.toggle("d-none");
      reviewAlert?.classList.add("d-none");
    });
    document.getElementById("btnSubmitReview")?.addEventListener("click", async () => {
      const user = window.WinIStore?.getSession?.();
      if (!user || !user.id) {
        window.location.href = "./login.html";
        return;
      }
      const rating = Number(document.getElementById("reviewRating")?.value || 5);
      const comment = String(document.getElementById("reviewComment")?.value || "").trim();
      if (!comment) {
        if (reviewAlert) {
          reviewAlert.textContent = "Vui lòng nhập bình luận.";
          reviewAlert.classList.remove("d-none");
        }
        return;
      }
      try {
        const res = await fetch(API_REVIEWS(p.id), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ userId: user.id, rating, comment }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
        if (reviewAlert) {
          reviewAlert.textContent = "Đăng đánh giá thành công.";
          reviewAlert.classList.remove("d-none");
          reviewAlert.classList.remove("alert-warning");
          reviewAlert.classList.add("alert-success");
          window.setTimeout(() => {
            reviewAlert.classList.add("d-none");
          }, 2000);
        }
        const commentEl = document.getElementById("reviewComment");
        if (commentEl) commentEl.value = "";
        await loadReviews(p.id);
      } catch (e) {
        if (reviewAlert) {
          reviewAlert.textContent = e?.message || "Không thể đăng đánh giá.";
          reviewAlert.classList.remove("d-none");
          reviewAlert.classList.remove("alert-success");
          reviewAlert.classList.add("alert-warning");
        }
      }
    });
    document.getElementById("reviewList")?.addEventListener("click", async (e) => {
      const btn = e.target.closest("button[data-action='deleteReview']");
      if (!btn) return;
      const reviewId = Number(btn.getAttribute("data-review-id"));
      const user = window.WinIStore?.getSession?.();
      if (!reviewId || !user?.id) return;
      if (!window.confirm("Bạn muốn gỡ đánh giá này?")) return;
      try {
        const res = await fetch(API_DELETE_REVIEW(p.id, reviewId, user.id), { method: "DELETE" });
        const ct = res.headers.get("content-type") || "";
        const data = ct.includes("application/json") ? await res.json() : null;
        if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
        await loadReviews(p.id);
      } catch (err) {
        if (reviewAlert) {
          reviewAlert.textContent = err?.message || "Không thể gỡ đánh giá.";
          reviewAlert.classList.remove("d-none");
          reviewAlert.classList.remove("alert-success");
          reviewAlert.classList.add("alert-warning");
        }
      }
    });
    void loadReviews(p.id);
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
