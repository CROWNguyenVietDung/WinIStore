(() => {
  const API_BY_IDS = "/api/public/products/by-ids";
  const API_CREATE_ORDER = "/api/public/orders";
  const SHIPPING_STANDARD = 30000;

  const { requireAuth, setSession, getSession, wireStoreHeader } = window.WinIStore;

  function fmtVnd(n) {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(n || 0);
    } catch {
      return `${n || 0} đ`;
    }
  }

  function getCart() {
    try {
      const raw = JSON.parse(localStorage.getItem("winstore_cart") || "[]");
      return Array.isArray(raw) ? raw : [];
    } catch {
      return [];
    }
  }

  function setCart(items) {
    localStorage.setItem("winstore_cart", JSON.stringify(items || []));
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

  function normalizeAddresses(user) {
    let list = Array.isArray(user.addresses) ? user.addresses : [];
    if (list.length === 0 && user.address) {
      list = [{ recipientName: user.fullName || "", phone: user.phone || "", detail: user.address, isDefault: true }];
    }
    if (list.length && !list.some((a) => a.isDefault)) {
      list[0] = { ...list[0], isDefault: true };
    }
    return list;
  }

  function persistUserAddresses(user, addresses) {
    const next = { ...user, addresses };
    setSession(next);
    return next;
  }

  function selectedPayment() {
    const el = document.querySelector("input[name='paymentMethod']:checked");
    return el ? el.value : "STORE_PICKUP";
  }

  function shippingFor(method) {
    return method === "STORE_PICKUP" ? 0 : SHIPPING_STANDARD;
  }

  function renderTotals(subtotal, method) {
    const ship = shippingFor(method);
    const total = subtotal + ship;
    document.getElementById("coSubtotal").textContent = fmtVnd(subtotal);
    document.getElementById("coShipping").textContent = ship === 0 ? "Miễn phí" : fmtVnd(ship);
    document.getElementById("coTotal").textContent = fmtVnd(total);
  }

  function renderAddressRadios(addresses, selectedIndex) {
    const host = document.getElementById("addressRadioGroup");
    const manualBlock = document.getElementById("manualAddressBlock");
    if (!host) return;
    host.innerHTML = "";
    if (!addresses.length) {
      const hint = document.createElement("div");
      hint.className = "text-muted";
      hint.textContent = "Chưa có địa chỉ trong sổ. Thêm địa chỉ hoặc nhập tay ở khung bên dưới.";
      host.appendChild(hint);
      manualBlock?.classList.remove("d-none");
      return;
    }
    manualBlock?.classList.add("d-none");
    addresses.forEach((a, i) => {
      const id = `addrRadio${i}`;
      const label = [a.recipientName || "—", a.phone || "", a.detail || ""].filter(Boolean).join(" · ");
      const row = document.createElement("div");
      row.className = "form-check mb-2";
      const input = document.createElement("input");
      input.className = "form-check-input";
      input.type = "radio";
      input.name = "addrPick";
      input.id = id;
      input.value = String(i);
      if (i === selectedIndex) input.checked = true;
      const lab = document.createElement("label");
      lab.className = "form-check-label";
      lab.htmlFor = id;
      lab.textContent = label;
      row.appendChild(input);
      row.appendChild(lab);
      host.appendChild(row);
    });
  }

  function getSelectedAddressIndex(addresses) {
    const el = document.querySelector("input[name='addrPick']:checked");
    if (!el || !addresses.length) return 0;
    const v = Number(el.value);
    return Number.isFinite(v) ? v : 0;
  }

  document.addEventListener("DOMContentLoaded", async () => {
    wireStoreHeader?.();
    document.getElementById("logoutHeaderBtn")?.addEventListener("click", () => window.WinIStore.goToLogin());

    const user = requireAuth("USER");
    if (!user) return;

    let items = getCart();
    if (!items.length) {
      window.location.href = "./cart.html";
      return;
    }

    let sessionUser = getSession() || user;
    let addresses = normalizeAddresses(sessionUser);

    const alertEl = document.getElementById("checkoutAlert");
    function showAlert(msg) {
      if (!alertEl) return;
      alertEl.textContent = msg || "";
      alertEl.classList.toggle("d-none", !msg);
    }

    const selectedIdx = Math.max(0, addresses.findIndex((a) => a.isDefault) >= 0 ? addresses.findIndex((a) => a.isDefault) : 0);

    let products = [];
    try {
      const ids = items.map((x) => x.productId);
      products = ids.length ? await fetchProductsByIds(ids) : [];
    } catch (e) {
      showAlert(e?.message || "Không tải được sản phẩm.");
    }
    const productsById = new Map(products.map((p) => [p.id, p]));

    const tbody = document.getElementById("checkoutLines");
    let subtotal = 0;
    tbody.innerHTML = items.map((it) => {
      const p = productsById.get(it.productId);
      const q = it.quantity || 1;
      const up = p ? unitPrice(p) : 0;
      const line = up * q;
      subtotal += line;
      return `
        <tr>
          <td>
            <div class="fw-semibold">${p?.name || "Sản phẩm"}</div>
            <div class="text-muted small">${p?.categoryName || ""}</div>
          </td>
          <td class="text-center">${q}</td>
          <td class="text-end">${fmtVnd(line)}</td>
        </tr>
      `;
    }).join("");

    function refreshTotals() {
      renderTotals(subtotal, selectedPayment());
    }

    document.querySelectorAll("input[name='paymentMethod']").forEach((r) => {
      r.addEventListener("change", () => {
        refreshTotals();
        const v = selectedPayment();
        document.getElementById("vnpayQrPanel")?.classList.toggle("d-none", v !== "VNPAY");
        document.getElementById("codHint")?.classList.toggle("d-none", v !== "COD");
        document.getElementById("pickupHint")?.classList.toggle("d-none", v !== "STORE_PICKUP");
      });
    });

    const vnpayPanel = document.getElementById("vnpayQrPanel");
    if (vnpayPanel) {
      vnpayPanel.classList.toggle("d-none", selectedPayment() !== "VNPAY");
    }
    document.getElementById("codHint")?.classList.toggle("d-none", selectedPayment() !== "COD");
    document.getElementById("pickupHint")?.classList.toggle("d-none", selectedPayment() !== "STORE_PICKUP");

    renderAddressRadios(addresses, addresses.length ? selectedIdx : 0);
    if (!addresses.length && sessionUser) {
      const mn = document.getElementById("coManualName");
      const mp = document.getElementById("coManualPhone");
      if (mn && !mn.value) mn.value = sessionUser.fullName || "";
      if (mp && !mp.value) mp.value = sessionUser.phone || "";
    }
    refreshTotals();

    document.getElementById("btnAddAddress")?.addEventListener("click", () => {
      const box = document.getElementById("newAddressForm");
      box?.classList.toggle("d-none");
    });

    document.getElementById("btnSaveNewAddress")?.addEventListener("click", () => {
      const name = (document.getElementById("newAddrName")?.value || "").trim();
      const phone = (document.getElementById("newAddrPhone")?.value || "").trim();
      const detail = (document.getElementById("newAddrDetail")?.value || "").trim();
      if (!name || !phone || !detail) {
        showAlert("Nhập đủ họ tên, SĐT và địa chỉ.");
        return;
      }
      showAlert("");
      const nextList = addresses.map((a) => ({ ...a, isDefault: false }));
      nextList.push({ recipientName: name, phone, detail, isDefault: true });
      addresses = nextList;
      sessionUser = persistUserAddresses(sessionUser, addresses);
      renderAddressRadios(addresses, addresses.length - 1);
      document.getElementById("newAddressForm")?.classList.add("d-none");
      document.getElementById("newAddrName").value = "";
      document.getElementById("newAddrPhone").value = "";
      document.getElementById("newAddrDetail").value = "";
    });

    document.getElementById("btnConfirmOrder")?.addEventListener("click", async () => {
      showAlert("");
      const pm = selectedPayment();
      const userId = Number(sessionUser?.id || user?.id);
      if (!Number.isFinite(userId) || userId <= 0) {
        showAlert("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        return;
      }
      let name = "";
      let phone = "";
      let addrText = "";

      if (addresses.length) {
        const i = getSelectedAddressIndex(addresses);
        const a = addresses[i] || {};
        name = (a.recipientName || "").trim();
        phone = (a.phone || "").trim();
        addrText = (a.detail || "").trim();
      } else {
        name = (document.getElementById("coManualName")?.value || "").trim();
        phone = (document.getElementById("coManualPhone")?.value || "").trim();
        addrText = (document.getElementById("coManualDetail")?.value || "").trim();
      }

      if (!name) name = (sessionUser.fullName || "").trim();
      if (!phone) phone = (sessionUser.phone || "").trim();

      if (pm === "STORE_PICKUP" && (!addrText || addrText.length < 3)) {
        addrText = "Nhận tại cửa hàng WinIStore";
      }

      if (pm !== "STORE_PICKUP" && !addrText) {
        showAlert("Vui lòng chọn hoặc nhập địa chỉ giao hàng.");
        return;
      }
      if (!name || !phone) {
        showAlert("Vui lòng nhập họ tên và số điện thoại người nhận.");
        return;
      }

      const btn = document.getElementById("btnConfirmOrder");
      try {
        btn.disabled = true;
        const payload = {
          userId,
          items: items.map((x) => ({ productId: x.productId, quantity: x.quantity || 1 })),
          paymentMethod: pm,
          recipientName: name,
          recipientPhone: phone,
          shippingAddress: addrText,
        };

        const res = await fetch(API_CREATE_ORDER, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        const ct = res.headers.get("content-type") || "";
        const data = ct.includes("application/json") ? await res.json() : null;
        if (!res.ok) {
          throw new Error((data && data.message) ? data.message : `HTTP ${res.status}`);
        }

        setCart([]);
        window.location.href = `./orders.html?success=1&orderId=${encodeURIComponent(String(data.orderId))}`;
      } catch (e) {
        showAlert(e?.message || "Không tạo được đơn hàng.");
      } finally {
        btn.disabled = false;
      }
    });
  });
})();
