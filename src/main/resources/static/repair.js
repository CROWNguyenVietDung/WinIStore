(() => {
  const API = "/api/public/repair-appointments";
  const { requireAuth, wireStoreHeader } = window.WinIStore;

  function pad2(n) {
    return String(n).padStart(2, "0");
  }

  function todayIsoLocal() {
    const d = new Date();
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
  }

  document.addEventListener("DOMContentLoaded", () => {
    wireStoreHeader?.();
    document.getElementById("logoutHeaderBtn")?.addEventListener("click", () => window.WinIStore.goToLogin());

    const user = requireAuth("USER");
    if (!user) return;

    const dateInput = document.getElementById("appointmentDate");
    if (dateInput) {
      dateInput.min = todayIsoLocal();
    }

    const form = document.getElementById("repairForm");
    const alertEl = document.getElementById("repairAlert");
    const okEl = document.getElementById("repairSuccess");
    const imagesInput = document.getElementById("repairImages");

    form?.addEventListener("submit", async (e) => {
      e.preventDefault();
      alertEl?.classList.add("d-none");
      okEl?.classList.add("d-none");

      const deviceName = (document.getElementById("deviceName")?.value || "").trim();
      const issueDescription = (document.getElementById("issueDescription")?.value || "").trim();
      const appointmentDate = (dateInput?.value || "").trim();

      if (!deviceName || !issueDescription || !appointmentDate) {
        if (alertEl) {
          alertEl.textContent = "Vui lòng điền đủ thông tin.";
          alertEl.classList.remove("d-none");
        }
        return;
      }

      const fd = new FormData();
      fd.append("userId", String(user.id));
      fd.append("deviceName", deviceName);
      fd.append("issueDescription", issueDescription);
      fd.append("appointmentDate", appointmentDate);

      const files = imagesInput && imagesInput.files ? Array.from(imagesInput.files) : [];
      for (let i = 0; i < files.length; i++) {
        fd.append("images", files[i]);
      }

      try {
        const res = await fetch(API, { method: "POST", body: fd });
        const text = await res.text();
        let data = null;
        try {
          data = text ? JSON.parse(text) : null;
        } catch {
          data = null;
        }
        if (!res.ok) {
          const msg =
            (data && data.message) ||
            (text && text.length < 400 && !text.trim().startsWith("<") ? text : null) ||
            `HTTP ${res.status}`;
          throw new Error(msg);
        }
        if (okEl) {
          okEl.textContent = "Đặt lịch thành công! Bạn có thể theo dõi trạng thái tại «Lịch hẹn của tôi».";
          okEl.classList.remove("d-none");
        }
        form.reset();
        if (dateInput) dateInput.min = todayIsoLocal();
        if (imagesInput) imagesInput.value = "";
      } catch (err) {
        if (alertEl) {
          alertEl.textContent = err?.message || "Không gửi được yêu cầu.";
          alertEl.classList.remove("d-none");
        }
      }
    });
  });
})();
