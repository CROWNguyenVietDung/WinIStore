// If FE is served by Spring Boot, same-origin requests work with empty base.
// If you later host FE elsewhere, you can change this to "http://localhost:8080".
(() => {
  const API_BASE = "";
  const API_PUBLIC_ORDERS = "/api/public/orders";
  const API_PUBLIC_PRODUCTS = "/api/public/products";

  function setSession(user) {
    localStorage.setItem("winstore_user", JSON.stringify(user));
  }

  function getSession() {
    const raw = localStorage.getItem("winstore_user");
    return raw ? JSON.parse(raw) : null;
  }

  function clearSession() {
    localStorage.removeItem("winstore_user");
  }

  function goToLogin() {
    clearSession();
    const base = (API_BASE || "").replace(/\/$/, "");
    if (base) {
      window.location.href = base + "/login.html";
      return;
    }
    if (typeof location !== "undefined" && location.protocol === "file:") {
      window.location.href = "../login.html";
      return;
    }
    window.location.href = "/login.html";
  }

  async function apiPost(path, body) {
    const res = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : await res.text();

    if (!res.ok) {
      let msg = "Yêu cầu thất bại";
      if (data && typeof data === "object") {
        msg = data.message || data.detail || data.error || data.title || msg;
      } else if (typeof data === "string" && data.length > 0 && data.length < 300 && !data.trim().startsWith("<")) {
        msg = data;
      }
      throw new Error(msg);
    }
    return data;
  }

  async function apiPut(path, body) {
    const res = await fetch(`${API_BASE}${path}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    const contentType = res.headers.get("content-type") || "";
    const data = contentType.includes("application/json") ? await res.json() : await res.text();

    if (!res.ok) {
      let msg = "Yêu cầu thất bại";
      if (data && typeof data === "object") {
        msg = data.message || data.detail || data.error || data.title || msg;
      } else if (typeof data === "string" && data.length > 0 && data.length < 300 && !data.trim().startsWith("<")) {
        msg = data;
      }
      throw new Error(msg);
    }
    return data;
  }

  /** Chuẩn hóa JSON từ AuthResponse (server) thành object lưu trong winstore_user. */
  function sessionFromAuthResponse(data, prev) {
    const prevObj = prev && typeof prev === "object" ? prev : {};
    const addrs = (data.addresses || []).map((a) => ({
      id: a.id,
      recipientName: a.recipientName || "",
      phone: a.recipientPhone || "",
      detail: a.detail || "",
      isDefault: !!a.isDefault,
    }));
    const def = addrs.find((x) => x.isDefault);
    return {
      ...prevObj,
      id: data.id,
      email: data.email,
      fullName: data.fullName,
      phone: data.phone,
      avatar: data.avatar != null ? data.avatar : prevObj.avatar,
      role: data.role,
      dob: data.dateOfBirth != null ? data.dateOfBirth : prevObj.dob || "",
      gender: prevObj.gender || "",
      addresses: addrs,
      address: def ? def.detail : prevObj.address || "",
    };
  }

  /** Đồng bộ họ tên, SĐT, ngày sinh và toàn bộ địa chỉ lên database. */
  async function syncUserProfile(user, addresses) {
    const u = user || getSession();
    if (!u || !u.id) throw new Error("Chưa đăng nhập.");
    const payload = {
      fullName: u.fullName || "",
      phone: u.phone || "",
      dateOfBirth: u.dob || null,
      addresses: (addresses || []).map((a) => ({
        recipientName: a.recipientName || "",
        recipientPhone: a.phone || "",
        detail: a.detail || "",
        isDefault: !!a.isDefault,
      })),
    };
    const data = await apiPut(`/api/auth/users/${u.id}/profile`, payload);
    return sessionFromAuthResponse(data, u);
  }

  function redirectByRole(role) {
    const path = role === "ADMIN" ? "/admin/dashboard.html" : "/user.html";
    const base = (API_BASE || "").replace(/\/$/, "");
    window.location.href = base ? base + path : path;
  }

  function requireAuth(expectedRole) {
    const u = getSession();
    if (!u) {
      const base = (API_BASE || "").replace(/\/$/, "");
      window.location.href = base ? base + "/login.html" : "/login.html";
      return null;
    }
    if (expectedRole && u.role !== expectedRole) {
      redirectByRole(u.role);
      return null;
    }
    return u;
  }

  /** Gỡ khỏi giỏ các sản phẩm không còn mở bán (API by-ids chỉ trả sản phẩm hiển thị). */
  async function pruneUnavailableCartItems() {
    let cart;
    try {
      cart = JSON.parse(localStorage.getItem("winstore_cart") || "[]");
    } catch {
      return;
    }
    if (!Array.isArray(cart) || !cart.length) return;
    const ids = [
      ...new Set(
        cart.map((x) => Number(x.productId)).filter((n) => Number.isFinite(n) && n > 0)
      ),
    ];
    if (!ids.length) return;
    const params = new URLSearchParams();
    ids.forEach((id) => params.append("ids", String(id)));
    try {
      const res = await fetch(`${API_BASE}${API_PUBLIC_PRODUCTS}/by-ids?${params.toString()}`);
      if (!res.ok) return;
      const data = await res.json();
      const visible = new Set((Array.isArray(data) ? data : []).map((p) => p.id));
      const next = cart.filter((x) => visible.has(Number(x.productId)));
      if (next.length !== cart.length) {
        localStorage.setItem("winstore_cart", JSON.stringify(next));
      }
    } catch {
      /* bỏ qua khi offline */
    }
  }

  function updateCartBadgeFromStorage() {
    const badge = document.getElementById("cartCountBadge");
    if (!badge) return;
    try {
      const cart = JSON.parse(localStorage.getItem("winstore_cart") || "[]");
      const n = (Array.isArray(cart) ? cart : []).reduce((s, x) => s + (Number(x?.quantity) || 0), 0);
      badge.textContent = String(n);
      badge.classList.toggle("d-none", n <= 0);
    } catch {
      badge.textContent = "0";
      badge.classList.add("d-none");
    }
  }

  async function syncCartCountBadge() {
    await pruneUnavailableCartItems();
    updateCartBadgeFromStorage();
  }

  function applySearchFieldsFromUrl() {
    try {
      const path = (typeof location !== "undefined" && location.pathname) || "";
      if (!path.endsWith("user.html") && !path.endsWith("/user.html")) return;
      const sp = new URLSearchParams(location.search || "");
      const searchQ = sp.get("search");
      const inp = document.getElementById("searchInput");
      if (searchQ && inp) inp.value = searchQ;
    } catch {
      /* ignore */
    }
  }

  function countUndeliveredOrders(list) {
    if (!Array.isArray(list)) return 0;
    return list.filter((o) => o.status === "PENDING" || o.status === "DELIVERING").length;
  }

  async function refreshOrderPendingBadge() {
    const badge = document.getElementById("orderPendingBadge");
    if (!badge) return;
    const u = getSession();
    if (!u || u.role !== "USER" || !u.id) {
      badge.classList.add("d-none");
      return;
    }
    try {
      const res = await fetch(`${API_PUBLIC_ORDERS}/by-user/${u.id}`);
      const ct = res.headers.get("content-type") || "";
      const data = ct.includes("application/json") ? await res.json() : null;
      if (!res.ok) throw new Error("bad");
      const n = countUndeliveredOrders(data);
      badge.textContent = String(n);
      badge.classList.toggle("d-none", n <= 0);
    } catch {
      badge.classList.add("d-none");
    }
  }

  function wireStoreHeader() {
    applySearchFieldsFromUrl();
    const searchBtn = document.getElementById("searchBtn");
    const searchInput = document.getElementById("searchInput");
    if (searchBtn && searchInput) {
      const runSearch = () => {
        const keyword = (searchInput.value || "").trim();
        if (!keyword) {
          window.location.href = "./user.html";
          return;
        }
        const params = new URLSearchParams();
        params.set("search", keyword);
        window.location.href = `./user.html?${params.toString()}`;
      };
      searchBtn.addEventListener("click", runSearch);
      searchInput.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter") runSearch();
      });
    }
    void syncCartCountBadge();
    void refreshOrderPendingBadge();
  }

  window.WinIStore = {
    apiPost,
    apiPut,
    setSession,
    getSession,
    clearSession,
    goToLogin,
    redirectByRole,
    requireAuth,
    wireStoreHeader,
    syncCartCountBadge,
    pruneUnavailableCartItems,
    refreshOrderPendingBadge,
    sessionFromAuthResponse,
    syncUserProfile,
    API_BASE,
  };
})();
