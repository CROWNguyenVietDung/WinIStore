(() => {
  const API_ORDERS = "/api/public/orders";

  const { requireAuth, wireStoreHeader } = window.WinIStore;

  const STATUS_LABEL = {
    PENDING: "Chờ xử lý",
    DELIVERING: "Đang giao",
    COMPLETED: "Đã giao",
    CANCELLED: "Đã hủy",
  };

  const PAYMENT_LABEL = {
    STORE_PICKUP: "Nhận tại cửa hàng",
    COD: "Thanh toán khi nhận hàng",
    VNPAY: "Thanh toán trực tiếp (VNPay)",
  };

  function fmtVnd(n) {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(Number(n || 0));
    } catch {
      return `${n || 0} đ`;
    }
  }

  function fmtDate(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString("vi-VN");
  }

  function badgeClass(status) {
    if (status === "PENDING") return "text-bg-warning";
    if (status === "DELIVERING") return "text-bg-info";
    if (status === "COMPLETED") return "text-bg-success";
    if (status === "CANCELLED") return "text-bg-secondary";
    return "text-bg-light text-dark";
  }

  function buildOrderCard(o) {
    const st = o.status || "";
    const pm = o.paymentMethod || "";
    const statusBadge = `<span class="badge ${badgeClass(st)}">${STATUS_LABEL[st] || st}</span>`;
    const paymentBadge = `<span class="badge text-bg-light border">${PAYMENT_LABEL[pm] || pm || "—"}</span>`;
    const items = Array.isArray(o.items) ? o.items : [];
    const itemsHtml = items.length
      ? items.map((it) => `<div class="small">- ${it.productName || "Sản phẩm"} x ${it.quantity || 0}</div>`).join("")
      : `<div class="small text-muted">Chưa có dữ liệu chi tiết sản phẩm.</div>`;
    return `
      <div class="card border-0 shadow-sm" style="border-radius:0.9rem;">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <div>
              <div class="fw-bold">Đơn #${o.id}</div>
              <div class="text-muted small">${fmtDate(o.createdAt)}</div>
            </div>
            <div class="text-end">
              <div class="fw-bold text-danger">${fmtVnd(o.totalPrice)}</div>
              <div class="small text-muted">${o.itemCount ?? 0} sản phẩm</div>
            </div>
          </div>
          <div class="d-flex flex-wrap gap-2 mb-2">${statusBadge}${paymentBadge}</div>
          <div class="mb-2">
            <div class="text-muted small fw-semibold">Sản phẩm</div>
            ${itemsHtml}
          </div>
          <div class="small">
            <div><span class="text-muted">Người nhận:</span> ${o.recipientName || "—"} - ${o.recipientPhone || "—"}</div>
            <div><span class="text-muted">Địa chỉ:</span> ${o.shippingAddress || "—"}</div>
          </div>
        </div>
      </div>
    `;
  }

  document.addEventListener("DOMContentLoaded", async () => {
    wireStoreHeader?.();
    document.getElementById("logoutHeaderBtn")?.addEventListener("click", () => window.WinIStore.goToLogin());

    const user = requireAuth("USER");
    if (!user) return;

    const banner = document.getElementById("ordersSuccessBanner");
    const params = new URLSearchParams(window.location.search || "");
    if (params.get("success") === "1" && banner) {
      const oid = params.get("orderId");
      banner.textContent = oid ? `Đặt hàng thành công! Mã đơn: ${oid}` : "Đặt hàng thành công!";
      banner.classList.remove("d-none");
      window.setTimeout(() => {
        banner.classList.add("d-none");
      }, 2000);
    }

    const activeList = document.getElementById("activeOrdersList");
    const historyList = document.getElementById("historyOrdersList");
    const emptyEl = document.getElementById("ordersEmpty");
    const alertEl = document.getElementById("ordersAlert");

    try {
      const res = await fetch(`${API_ORDERS}/by-user/${user.id}`, { method: "GET" });
      const ct = res.headers.get("content-type") || "";
      const data = ct.includes("application/json") ? await res.json() : null;
      if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
      const list = Array.isArray(data) ? data : [];

      if (!activeList || !historyList) return;
      activeList.innerHTML = "";
      historyList.innerHTML = "";

      if (!list.length) {
        emptyEl?.classList.remove("d-none");
        return;
      }
      emptyEl?.classList.add("d-none");

      const active = list.filter((o) => o.status === "PENDING" || o.status === "DELIVERING");
      const history = list.filter((o) => o.status === "COMPLETED" || o.status === "CANCELLED");

      activeList.innerHTML = active.length
        ? active.map(buildOrderCard).join("")
        : `<div class="text-muted">Không có đơn chưa giao.</div>`;
      historyList.innerHTML = history.length
        ? history.map(buildOrderCard).join("")
        : `<div class="text-muted">Chưa có lịch sử nhận hàng.</div>`;
    } catch (e) {
      if (alertEl) {
        alertEl.textContent = e?.message || "Không tải được danh sách đơn.";
        alertEl.classList.remove("d-none");
      }
    }
  });
})();
