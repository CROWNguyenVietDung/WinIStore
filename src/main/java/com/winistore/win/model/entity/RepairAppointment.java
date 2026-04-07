package com.winistore.win.model.entity;

import com.winistore.win.model.enums.RepairAppointmentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "RepairAppointment")
public class RepairAppointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false, length = 150)
    private String deviceName;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepairAppointmentStatus status;

    @Column(name = "actual_cost", precision = 18, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "suggested_dates_csv", length = 1000)
    private String suggestedDatesCsv;

    @OneToMany(mappedBy = "repairAppointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<RepairAppointmentImage> images = new ArrayList<>();

    public RepairAppointment() {
    }

    public RepairAppointment(Long id, User user, String deviceName, String issueDescription, LocalDate appointmentDate,
                             RepairAppointmentStatus status, BigDecimal actualCost, String suggestedDatesCsv,
                             List<RepairAppointmentImage> images) {
        this.id = id;
        this.user = user;
        this.deviceName = deviceName;
        this.issueDescription = issueDescription;
        this.appointmentDate = appointmentDate;
        this.status = status;
        this.actualCost = actualCost;
        this.suggestedDatesCsv = suggestedDatesCsv;
        this.images = images == null ? new ArrayList<>() : images;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private User user;
        private String deviceName;
        private String issueDescription;
        private LocalDate appointmentDate;
        private RepairAppointmentStatus status;
        private BigDecimal actualCost;
        private String suggestedDatesCsv;
        private List<RepairAppointmentImage> images;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder deviceName(String deviceName) { this.deviceName = deviceName; return this; }
        public Builder issueDescription(String issueDescription) { this.issueDescription = issueDescription; return this; }
        public Builder appointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; return this; }
        public Builder status(RepairAppointmentStatus status) { this.status = status; return this; }
        public Builder actualCost(BigDecimal actualCost) { this.actualCost = actualCost; return this; }
        public Builder suggestedDatesCsv(String suggestedDatesCsv) { this.suggestedDatesCsv = suggestedDatesCsv; return this; }
        public Builder images(List<RepairAppointmentImage> images) { this.images = images; return this; }

        public RepairAppointment build() {
            return new RepairAppointment(id, user, deviceName, issueDescription, appointmentDate, status, actualCost, suggestedDatesCsv, images);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getIssueDescription() { return issueDescription; }
    public void setIssueDescription(String issueDescription) { this.issueDescription = issueDescription; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public RepairAppointmentStatus getStatus() { return status; }
    public void setStatus(RepairAppointmentStatus status) { this.status = status; }
    public BigDecimal getActualCost() { return actualCost; }
    public void setActualCost(BigDecimal actualCost) { this.actualCost = actualCost; }
    public String getSuggestedDatesCsv() { return suggestedDatesCsv; }
    public void setSuggestedDatesCsv(String suggestedDatesCsv) { this.suggestedDatesCsv = suggestedDatesCsv; }
    public List<RepairAppointmentImage> getImages() { return images; }
    public void setImages(List<RepairAppointmentImage> images) { this.images = images == null ? new ArrayList<>() : images; }
}
