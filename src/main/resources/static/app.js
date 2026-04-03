// If FE is served by Spring Boot, same-origin requests work with empty base.
// If you later host FE elsewhere, you can change this to "http://localhost:8080".
(() => {
  const API_BASE = "";
  const API_PUBLIC_ORDERS = "/api/public/orders";

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

  function syncCartCountBadge() {
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
    const searchBtn = document.getElementById("searchBtn");
    const searchInput = document.getElementById("searchInput");
    if (searchBtn && searchInput) {
      const runSearch = () => {
        const keyword = (searchInput.value || "").trim();
        if (!keyword) {
          window.location.href = "./user.html";
          return;
        }
        window.location.href = `./user.html?categoryName=${encodeURIComponent(keyword)}`;
      };
      searchBtn.addEventListener("click", runSearch);
      searchInput.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter") runSearch();
      });
    }
    syncCartCountBadge();
    void refreshOrderPendingBadge();
  }

  window.WinIStore = {
    apiPost,
    setSession,
    getSession,
    clearSession,
    goToLogin,
    redirectByRole,
    requireAuth,
    wireStoreHeader,
    syncCartCountBadge,
    refreshOrderPendingBadge,
    API_BASE,
  };
})();
