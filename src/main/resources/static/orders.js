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

  function escapeHtml(text) {
    if (text == null || text === "") return "";
    const d = document.createElement("div");
    d.textContent = String(text);
    return d.innerHTML;
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
    const canCancel = st === "PENDING";
    const actionHtml = canCancel
      ? `<button class="btn btn-sm btn-outline-danger js-cancel-order" data-order-id="${o.id}">Hủy đơn</button>`
      : "";
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
            ${o.customerNote ? `<div class="mt-2"><span class="text-muted">Ghi chú:</span> ${escapeHtml(o.customerNote)}</div>` : ""}
          </div>
          ${actionHtml ? `<div class="mt-3 d-flex justify-content-end">${actionHtml}</div>` : ""}
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
    const cancelPanel = document.getElementById("cancelOrderPanel");
    const cancelMetaEl = document.getElementById("cancelOrderMeta");
    const cancelItemsEl = document.getElementById("cancelOrderItems");
    const cancelOtherEl = document.getElementById("cancelReasonOther");
    const cancelSubmitBtn = document.getElementById("cancelOrderSubmitBtn");
    const cancelCloseBtn = document.getElementById("cancelOrderCloseBtn");
    let selectedCancelOrder = null;
    let orderMap = new Map();

    const renderOrders = async () => {
      const res = await fetch(`${API_ORDERS}/by-user/${user.id}`, { method: "GET" });
      const ct = res.headers.get("content-type") || "";
      const data = ct.includes("application/json") ? await res.json() : null;
      if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
      const list = Array.isArray(data) ? data : [];
      orderMap = new Map(list.map((o) => [Number(o.id), o]));

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
    };

    try {
      await renderOrders();
    } catch (e) {
      if (alertEl) {
        alertEl.textContent = e?.message || "Không tải được danh sách đơn.";
        alertEl.classList.remove("d-none");
      }
    }

    document.addEventListener("click", async (evt) => {
      const btn = evt.target.closest(".js-cancel-order");
      if (!btn) return;
      const orderId = Number(btn.getAttribute("data-order-id"));
      if (!orderId) return;
      const order = orderMap.get(orderId);
      if (!order) return;

      selectedCancelOrder = order;
      if (cancelMetaEl) {
        cancelMetaEl.textContent = `Đơn #${order.id} - ${fmtDate(order.createdAt)} - ${fmtVnd(order.totalPrice)}`;
      }
      if (cancelItemsEl) {
        const items = Array.isArray(order.items) ? order.items : [];
        cancelItemsEl.innerHTML = items.length
          ? items.map((it) => `<div>- ${escapeHtml(it.productName || "Sản phẩm")} x ${it.quantity || 0}</div>`).join("")
          : `<div class="text-muted">Không có dữ liệu sản phẩm.</div>`;
      }
      document.querySelectorAll("input[name='cancelReasonOption']").forEach((r) => {
        r.checked = false;
      });
      if (cancelOtherEl) cancelOtherEl.value = "";
      cancelPanel?.classList.remove("d-none");
      cancelPanel?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    cancelCloseBtn?.addEventListener("click", () => {
      selectedCancelOrder = null;
      cancelPanel?.classList.add("d-none");
    });

    cancelSubmitBtn?.addEventListener("click", async () => {
      if (!selectedCancelOrder) return;
      const selected = document.querySelector("input[name='cancelReasonOption']:checked");
      const selectedReason = selected ? String(selected.value || "").trim() : "";
      const otherReason = String(cancelOtherEl?.value || "").trim();
      const reason = otherReason || selectedReason;

      if (!reason) {
        if (alertEl) {
          alertEl.textContent = "Vui lòng chọn hoặc nhập lý do hủy đơn hàng.";
          alertEl.classList.remove("d-none");
        }
        return;
      }
      if (!window.confirm(`Bạn chắc chắn muốn hủy đơn #${selectedCancelOrder.id}?`)) {
        return;
      }

      try {
        cancelSubmitBtn.disabled = true;
        const res = await fetch(`${API_ORDERS}/${selectedCancelOrder.id}/cancel`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ userId: user.id, reason }),
        });
        const ct = res.headers.get("content-type") || "";
        const data = ct.includes("application/json") ? await res.json() : null;
        if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);

        if (banner) {
          banner.textContent = "Hủy đơn hàng thành công.";
          banner.classList.remove("d-none");
          window.setTimeout(() => banner.classList.add("d-none"), 2200);
        }
        alertEl?.classList.add("d-none");
        selectedCancelOrder = null;
        cancelPanel?.classList.add("d-none");
        await renderOrders();
      } catch (e) {
        if (alertEl) {
          alertEl.textContent = e?.message || "Không thể hủy đơn hàng.";
          alertEl.classList.remove("d-none");
        }
      } finally {
        cancelSubmitBtn.disabled = false;
      }
    });
  });
})();
