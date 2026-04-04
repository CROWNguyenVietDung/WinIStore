(() => {
  const API_BY_IDS = "/api/public/products/by-ids";
  const SHIPPING = 30000;

  const els = {
    list: () => document.getElementById("cartList"),
    empty: () => document.getElementById("cartEmpty"),
    alert: () => document.getElementById("cartAlert"),
    clearBtn: () => document.getElementById("clearCartBtn"),
    checkoutBtn: () => document.getElementById("checkoutBtn"),

    summaryName: () => document.getElementById("summaryName"),
    summaryCategory: () => document.getElementById("summaryCategory"),
    summaryOriginalPrice: () => document.getElementById("summaryOriginalPrice"),
    summaryPrice: () => document.getElementById("summaryPrice"),
    summaryDiscountWrap: () => document.getElementById("summaryDiscountWrap"),
    summaryDiscountBadge: () => document.getElementById("summaryDiscountBadge"),

    subtotalText: () => document.getElementById("subtotalText"),
    shippingText: () => document.getElementById("shippingText"),
    totalText: () => document.getElementById("totalText"),
  };

  const fmtVnd = (n) => {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(n || 0);
    } catch {
      return `${n || 0} đ`;
    }
  };

  function showAlert(msg) {
    const a = els.alert();
    if (!a) return;
    a.textContent = msg || "";
    a.classList.toggle("d-none", !msg);
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

  function cartCount(cart) {
    return (cart || []).reduce((sum, x) => sum + (Number(x?.quantity) || 0), 0);
  }

  function updateCartBadge(items) {
    const badge = document.getElementById("cartCountBadge");
    if (!badge) return;
    const count = cartCount(items);
    badge.textContent = String(count);
    badge.classList.toggle("d-none", count <= 0);
  }

  function unitPrice(p) {
    const price = Number(p?.price || 0);
    const d = Number(p?.discountPercent || 0);
    if (d > 0 && d <= 100) return Math.round(price * (100 - d) / 100);
    return price;
  }

  async function fetchProductsByIds(ids) {
    const params = new URLSearchParams();
    ids.forEach((id) => params.append("ids", String(id)));
    const res = await fetch(`${API_BY_IDS}?${params}`, { method: "GET" });
    const ct = res.headers.get("content-type") || "";
    const data = ct.includes("application/json") ? await res.json() : null;
    if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
    return Array.isArray(data) ? data : [];
  }

  function render(items, productsById, selectedId) {
    const listEl = els.list();
    const emptyEl = els.empty();
    if (!listEl || !emptyEl) return;

    if (!items || items.length === 0) {
      listEl.innerHTML = "";
      emptyEl.classList.remove("d-none");
      setSummary(null, 0);
      setTotals(0);
      return;
    }
    emptyEl.classList.add("d-none");

    listEl.innerHTML = items.map((it) => {
      const p = productsById.get(it.productId);
      const img = p?.imageUrl || "https://placehold.co/600x400/png?text=No+Image";
      const q = it.quantity || 1;
      const up = p ? unitPrice(p) : 0;
      const line = up * q;
      const discount = Number(p?.discountPercent || 0);
      const badge = discount > 0 ? `<span class="discount-badge">-${discount}%</span>` : "";
      return `
        <div class="cart-item p-3 d-flex gap-3 align-items-center ${selectedId === it.productId ? "active" : ""}"
             data-action="select" data-id="${it.productId}">
          <img class="rounded-3 cart-img border" src="${img}" alt="">
          <div class="flex-grow-1">
            <div class="fw-semibold">${p?.name || "Sản phẩm"}</div>
            <div class="text-muted small">${p?.categoryName || ""}</div>
            <div class="mt-2 d-flex align-items-center gap-2">
              <div class="fw-bold price-sale">${fmtVnd(line)}</div>
              ${badge}
            </div>
          </div>
          <div class="d-flex flex-column align-items-end gap-2">
            <div class="input-group input-group-sm" style="width: 140px;">
              <button class="btn btn-outline-secondary" type="button" data-action="dec" data-id="${it.productId}">-</button>
              <input class="form-control text-center" value="${q}" readonly />
              <button class="btn btn-outline-secondary" type="button" data-action="inc" data-id="${it.productId}">+</button>
            </div>
            <button class="btn btn-outline-danger btn-sm" type="button" data-action="remove" data-id="${it.productId}">
              <i class="bi bi-trash3"></i>
            </button>
          </div>
        </div>
      `;
    }).join("");

    const selectedItem = items.find((x) => x.productId === selectedId) || items[0];
    const selectedProduct = productsById.get(selectedItem.productId) || null;
    setSummary(selectedProduct, selectedItem.quantity || 1);
    setTotals(calcSubtotal(items, productsById));
  }

  function calcSubtotal(items, productsById) {
    return (items || []).reduce((sum, it) => {
      const p = productsById.get(it.productId);
      if (!p) return sum;
      const q = it.quantity || 1;
      return sum + unitPrice(p) * q;
    }, 0);
  }

  function setTotals(subtotal) {
    els.subtotalText().textContent = fmtVnd(subtotal);
    els.shippingText().textContent = fmtVnd(SHIPPING);
    els.totalText().textContent = fmtVnd(subtotal + SHIPPING);
  }

  function setSummary(p, qty) {
    if (!p) {
      els.summaryName().textContent = "—";
      els.summaryCategory().textContent = "—";
      els.summaryOriginalPrice().classList.add("d-none");
      els.summaryOriginalPrice().textContent = "—";
      els.summaryPrice().textContent = "—";
      els.summaryDiscountWrap().classList.add("d-none");
      els.summaryDiscountBadge().textContent = "-0%";
      return;
    }

    els.summaryName().textContent = p.name || "—";
    els.summaryCategory().textContent = p.categoryName ? `Danh mục: ${p.categoryName}` : "—";

    const price = Number(p.price || 0);
    const d = Number(p.discountPercent || 0);
    const hasDiscount = d > 0 && d <= 100;
    const up = unitPrice(p);
    const line = up * (qty || 1);

    if (hasDiscount) {
      els.summaryOriginalPrice().classList.remove("d-none");
      els.summaryOriginalPrice().textContent = fmtVnd(price * (qty || 1));
      els.summaryDiscountWrap().classList.remove("d-none");
      els.summaryDiscountBadge().textContent = `-${d}%`;
    } else {
      els.summaryOriginalPrice().classList.add("d-none");
      els.summaryDiscountWrap().classList.add("d-none");
    }
    els.summaryPrice().textContent = fmtVnd(line);
  }

  document.addEventListener("DOMContentLoaded", async () => {
    let items = getCart();
    let selectedId = items[0]?.productId || null;
    updateCartBadge(items);

    let products = [];
    try {
      const ids = items.map((x) => x.productId);
      products = ids.length ? await fetchProductsByIds(ids) : [];
    } catch (e) {
      showAlert(e?.message || "Không tải được dữ liệu giỏ hàng.");
    }

    const visibleIds = new Set(products.map((p) => p.id));
    const pruned = items.filter((it) => visibleIds.has(it.productId));
    if (pruned.length !== items.length) {
      setCart(pruned);
      items = pruned;
      selectedId = items[0]?.productId || null;
      showAlert("Một số sản phẩm không còn mở bán đã được gỡ khỏi giỏ hàng.");
    }

    const productsById = new Map(products.map((p) => [p.id, p]));
    render(items, productsById, selectedId);

    els.clearBtn()?.addEventListener("click", () => {
      setCart([]);
      items = [];
      updateCartBadge(items);
      render(items, productsById, null);
    });

    els.list()?.addEventListener("click", async (e) => {
      const el = e.target.closest("[data-action]");
      if (!el) return;
      const action = el.getAttribute("data-action");
      const id = Number(el.getAttribute("data-id"));

      if (action === "select") {
        selectedId = id;
        render(items, productsById, selectedId);
        return;
      }

      if (action === "remove") {
        items = items.filter((x) => x.productId !== id);
        setCart(items);
        updateCartBadge(items);
        selectedId = items[0]?.productId || null;
        render(items, productsById, selectedId);
        return;
      }

      const idx = items.findIndex((x) => x.productId === id);
      if (idx === -1) return;
      if (action === "inc") items[idx].quantity = (items[idx].quantity || 1) + 1;
      if (action === "dec") items[idx].quantity = Math.max(1, (items[idx].quantity || 1) - 1);
      setCart(items);
      updateCartBadge(items);
      render(items, productsById, selectedId);
    });

    els.checkoutBtn()?.addEventListener("click", () => {
      if (!items.length) {
        showAlert("Giỏ hàng đang trống.");
        return;
      }
      const session = window?.WinIStore?.getSession?.() || null;
      if (!session || session.role !== "USER") {
        showAlert("Vui lòng đăng nhập tài khoản khách hàng để thanh toán.");
        window.location.href = "./login.html";
        return;
      }
      window.location.href = "./checkout.html";
    });
  });
})();

