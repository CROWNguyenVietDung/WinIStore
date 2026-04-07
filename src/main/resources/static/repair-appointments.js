(() => {
  const API = "/api/public/repair-appointments";
  const { requireAuth, wireStoreHeader, apiPost } = window.WinIStore;

  const STATUS_LABEL = {
    PENDING: "Chờ xác nhận",
    CONFIRMED: "Đã xác nhận",
    IN_PROGRESS: "Đang sửa",
    COMPLETED: "Đã xong",
    CANCELLED: "Đã hủy",
  };

  const ACTIVE = ["PENDING", "CONFIRMED", "IN_PROGRESS"];
  const HISTORY = ["COMPLETED", "CANCELLED"];

  let listCache = [];

  function fmtVnd(n) {
    try {
      return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(Number(n || 0));
    } catch {
      return `${n || 0} đ`;
    }
  }

  function fmtDateOnly(v) {
    if (!v) return "—";
    const m = String(v).match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (m) {
      const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
      return d.toLocaleDateString("vi-VN", { weekday: "long", year: "numeric", month: "long", day: "numeric" });
    }
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return String(v);
    return d.toLocaleDateString("vi-VN");
  }

  function escapeHtml(text) {
    if (text == null || text === "") return "";
    const el = document.createElement("div");
    el.textContent = String(text);
    return el.innerHTML;
  }

  function badgeClass(status) {
    if (status === "PENDING") return "text-bg-warning";
    if (status === "CONFIRMED") return "text-bg-primary";
    if (status === "IN_PROGRESS") return "text-bg-info";
    if (status === "COMPLETED") return "text-bg-success";
    if (status === "CANCELLED") return "text-bg-secondary";
    return "text-bg-light text-dark";
  }

  function buildCard(r) {
    const st = r.status || "";
    const badge = `<span class="badge ${badgeClass(st)}">${STATUS_LABEL[st] || st}</span>`;
    const cost =
      st === "COMPLETED" && r.actualCost != null
        ? `<div class="mt-2 small"><span class="text-muted">Chi phí:</span> <span class="fw-bold text-danger">${fmtVnd(r.actualCost)}</span></div>`
        : "";
    const cancelBtn =
      st === "PENDING"
        ? `<button type="button" class="btn btn-outline-danger btn-sm mt-2 me-1" data-cancel-id="${r.id}">Hủy lịch</button>`
        : "";
    const options = Array.isArray(r.suggestedDates) ? r.suggestedDates : [];
    const rescheduleBox =
      st === "PENDING" && options.length
        ? `
          <div class="mt-2 p-2 border rounded bg-light">
            <div class="small fw-semibold mb-1">Admin đề xuất ngày khác</div>
            <div class="d-flex flex-wrap gap-1 mb-2">
              ${options.map((d) => `<span class="badge text-bg-light border">${fmtDateOnly(d)}</span>`).join("")}
            </div>
            <div class="d-flex flex-wrap gap-2">
              <select class="form-select form-select-sm" style="max-width:260px" data-choose-date-select="${r.id}">
                <option value="">Chọn ngày bạn muốn hẹn</option>
                ${options.map((d) => `<option value="${d}">${fmtDateOnly(d)}</option>`).join("")}
              </select>
              <button type="button" class="btn btn-sm btn-primary" data-choose-date-id="${r.id}">Xác nhận ngày</button>
            </div>
          </div>
        `
        : "";
    const detailBtn = `<button type="button" class="btn btn-outline-primary btn-sm mt-2" data-detail-id="${r.id}">Xem chi tiết</button>`;
    const thumbCount = Array.isArray(r.imageUrls) ? r.imageUrls.length : 0;
    const thumbs =
      thumbCount > 0
        ? `<div class="d-flex flex-wrap gap-1 mt-2">${r.imageUrls
            .slice(0, 4)
            .map((u) => {
              const src = String(u || "").replace(/"/g, "");
              return `<img src="${src}" alt="" class="rounded border" style="width:56px;height:56px;object-fit:cover;" />`;
            })
            .join("")}${thumbCount > 4 ? `<span class="small text-muted align-self-center ms-1">+${thumbCount - 4}</span>` : ""}</div>`
        : "";
    return `
      <div class="card border-0 shadow-sm" style="border-radius:0.9rem;" data-row-id="${r.id}">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 flex-wrap">
            <div>
              <div class="fw-bold">${escapeHtml(r.deviceName || "Thiết bị")}</div>
              <div class="text-muted small">${fmtDateOnly(r.appointmentDate)}</div>
            </div>
            <div>${badge}</div>
          </div>
          <div class="mt-2 small text-secondary text-truncate" style="max-width:100%">${escapeHtml(r.issueDescription || "")}</div>
          ${thumbs}
          ${cost}
          ${rescheduleBox}
          <div class="d-flex flex-wrap gap-1">${cancelBtn}${detailBtn}</div>
        </div>
      </div>
    `;
  }

  function showDetailModal(r) {
    const modalEl = document.getElementById("repairDetailModal");
    if (!modalEl) return;
    document.getElementById("detailDevice").textContent = r.deviceName || "—";
    document.getElementById("detailDate").textContent = fmtDateOnly(r.appointmentDate);
    document.getElementById("detailStatus").textContent = STATUS_LABEL[r.status] || r.status || "—";
    const costRow = document.getElementById("detailCostRow");
    const costEl = document.getElementById("detailCost");
    if (r.status === "COMPLETED" && r.actualCost != null) {
      costRow.classList.remove("d-none");
      costEl.textContent = fmtVnd(r.actualCost);
    } else {
      costRow.classList.add("d-none");
    }
    document.getElementById("detailIssue").textContent = r.issueDescription || "—";
    const wrap = document.getElementById("detailImages");
    const noImg = document.getElementById("detailNoImages");
    const urls = Array.isArray(r.imageUrls) ? r.imageUrls : [];
    if (urls.length && wrap) {
      wrap.innerHTML = urls
        .map((u) => {
          const src = String(u || "").replace(/"/g, "");
          return `<a href="${src}" target="_blank" rel="noopener" class="d-inline-block"><img src="${src}" class="img-thumbnail" style="max-height:160px;object-fit:cover;" alt="" /></a>`;
        })
        .join("");
      wrap.classList.remove("d-none");
      noImg.classList.add("d-none");
    } else {
      wrap.innerHTML = "";
      wrap.classList.add("d-none");
      noImg.classList.remove("d-none");
    }
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  async function loadList(user, activeEl, historyEl, emptyEl, activeHint, historyHint, alertEl) {
    const res = await fetch(`${API}/by-user/${user.id}`, { method: "GET" });
    const ct = res.headers.get("content-type") || "";
    const data = ct.includes("application/json") ? await res.json() : null;
    if (!res.ok) throw new Error((data && data.message) || `HTTP ${res.status}`);
    const list = Array.isArray(data) ? data : [];
    listCache = list;

    if (!list.length) {
      emptyEl?.classList.remove("d-none");
      activeEl.innerHTML = "";
      historyEl.innerHTML = "";
      activeHint?.classList.add("d-none");
      historyHint?.classList.add("d-none");
      return;
    }
    emptyEl?.classList.add("d-none");

    const act = list.filter((r) => ACTIVE.includes(r.status));
    const hist = list.filter((r) => HISTORY.includes(r.status));

    activeEl.innerHTML = act.length ? act.map(buildCard).join("") : "";
    historyEl.innerHTML = hist.length ? hist.map(buildCard).join("") : "";

    if (activeHint) {
      activeHint.classList.toggle("d-none", act.length > 0);
    }
    if (historyHint) {
      historyHint.classList.toggle("d-none", hist.length > 0);
    }
  }

  document.addEventListener("DOMContentLoaded", async () => {
    wireStoreHeader?.();
    document.getElementById("logoutHeaderBtn")?.addEventListener("click", () => window.WinIStore.goToLogin());

    const user = requireAuth("USER");
    if (!user) return;

    const activeEl = document.getElementById("appointmentsActiveList");
    const historyEl = document.getElementById("appointmentsHistoryList");
    const emptyEl = document.getElementById("appointmentsEmpty");
    const activeHint = document.getElementById("appointmentsActiveHint");
    const historyHint = document.getElementById("appointmentsHistoryHint");
    const alertEl = document.getElementById("listAlert");

    try {
      await loadList(user, activeEl, historyEl, emptyEl, activeHint, historyHint, alertEl);
    } catch (e) {
      if (alertEl) {
        alertEl.textContent = e?.message || "Không tải được danh sách.";
        alertEl.classList.remove("d-none");
      }
    }

    const root = document.querySelector(".container.py-4");
    root?.addEventListener("click", async (e) => {
      const detailBtn = e.target.closest("button[data-detail-id]");
      if (detailBtn) {
        const id = Number(detailBtn.getAttribute("data-detail-id"));
        const r = listCache.find((x) => x.id === id);
        if (r) showDetailModal(r);
        return;
      }

      const btn = e.target.closest("button[data-cancel-id]");
      if (btn) {
        const id = Number(btn.getAttribute("data-cancel-id"));
        if (!id || !window.confirm("Bạn có chắc muốn hủy lịch này?")) return;
        alertEl?.classList.add("d-none");
        try {
          await apiPost(`${API}/${id}/cancel`, { userId: user.id });
          await loadList(user, activeEl, historyEl, emptyEl, activeHint, historyHint, alertEl);
        } catch (err) {
          if (alertEl) {
            alertEl.textContent = err?.message || "Không hủy được lịch.";
            alertEl.classList.remove("d-none");
          }
        }
        return;
      }

      const chooseBtn = e.target.closest("button[data-choose-date-id]");
      if (!chooseBtn) return;
      const id = Number(chooseBtn.getAttribute("data-choose-date-id"));
      if (!id) return;
      const select = root.querySelector(`select[data-choose-date-select='${id}']`);
      const selectedDate = (select && select.value ? String(select.value).trim() : "");
      if (!selectedDate) {
        if (alertEl) {
          alertEl.textContent = "Vui lòng chọn ngày hẹn.";
          alertEl.classList.remove("d-none");
        }
        return;
      }
      if (!window.confirm("Xác nhận ngày hẹn này?")) return;
      alertEl?.classList.add("d-none");
      try {
        await apiPost(`${API}/${id}/choose-date`, { userId: user.id, selectedDate });
        await loadList(user, activeEl, historyEl, emptyEl, activeHint, historyHint, alertEl);
      } catch (err) {
        if (alertEl) {
          alertEl.textContent = err?.message || "Không xác nhận được ngày hẹn.";
          alertEl.classList.remove("d-none");
        }
      }
    });
  });
})();
