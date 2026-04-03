package com.winistore.win.model.entity;

import com.winistore.win.model.enums.Role;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "User")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "avatar", length = 500)
    private String avatar;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepairAppointment> repairAppointments = new ArrayList<>();

    public User() {
    }

    public User(Long id, String username, String fullName, String password, String email, String phone, Role role, String avatar,
                LocalDate dateOfBirth,
                List<Address> addresses, List<Order> orders, List<RepairAppointment> repairAppointments) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.avatar = avatar;
        this.dateOfBirth = dateOfBirth;
        this.addresses = addresses != null ? addresses : new ArrayList<>();
        this.orders = orders != null ? orders : new ArrayList<>();
        this.repairAppointments = repairAppointments != null ? repairAppointments : new ArrayList<>();
    }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private Long id;
        private String username;
        private String fullName;
        private String password;
        private String email;
        private String phone;
        private Role role;
        private String avatar;
        private LocalDate dateOfBirth;
        private List<Address> addresses;
        private List<Order> orders;
        private List<RepairAppointment> repairAppointments;

        public UserBuilder id(Long id) { this.id = id; return this; }
        public UserBuilder username(String username) { this.username = username; return this; }
        public UserBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public UserBuilder password(String password) { this.password = password; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder phone(String phone) { this.phone = phone; return this; }
        public UserBuilder role(Role role) { this.role = role; return this; }
        public UserBuilder avatar(String avatar) { this.avatar = avatar; return this; }
        public UserBuilder dateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; return this; }
        public UserBuilder addresses(List<Address> addresses) { this.addresses = addresses; return this; }
        public UserBuilder orders(List<Order> orders) { this.orders = orders; return this; }
        public UserBuilder repairAppointments(List<RepairAppointment> repairAppointments) { this.repairAppointments = repairAppointments; return this; }

        public User build() {
            return new User(id, username, fullName, password, email, phone, role, avatar, dateOfBirth, addresses, orders, repairAppointments);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }
    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
    public List<RepairAppointment> getRepairAppointments() { return repairAppointments; }
    public void setRepairAppointments(List<RepairAppointment> repairAppointments) { this.repairAppointments = repairAppointments; }
}
