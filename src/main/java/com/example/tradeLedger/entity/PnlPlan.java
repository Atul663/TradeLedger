package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "pnl_plan",
        uniqueConstraints = @UniqueConstraint(name = "uq_pnl_plan_user_name", columnNames = {"user_id", "plan_name"})
)
public class PnlPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserDetails user;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "annual_target", nullable = false, precision = 18, scale = 2)
    private BigDecimal annualTarget;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "plan_type", nullable = false, length = 50, columnDefinition = "varchar(50) default 'FNO'")
    private String planType = "FNO";

    @Column(name = "starting_capital", nullable = false, precision = 18, scale = 2, columnDefinition = "numeric(18,2) default 0.00")
    private BigDecimal startingCapital = BigDecimal.ZERO;

    @Column(name = "total_achieved_amount", nullable = false, precision = 18, scale = 2, columnDefinition = "numeric(18,2) default 0.00")
    private BigDecimal totalAchievedAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PnlPlanMonth> months;

    public PnlPlan() {}

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserDetails getUser() { return user; }
    public void setUser(UserDetails user) { this.user = user; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getAnnualTarget() { return annualTarget; }
    public void setAnnualTarget(BigDecimal annualTarget) { this.annualTarget = annualTarget; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<PnlPlanMonth> getMonths() { return months; }
    public void setMonths(List<PnlPlanMonth> months) { this.months = months; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public BigDecimal getStartingCapital() { return startingCapital; }
    public void setStartingCapital(BigDecimal startingCapital) { this.startingCapital = startingCapital; }

    public BigDecimal getTotalAchievedAmount() { return totalAchievedAmount; }
    public void setTotalAchievedAmount(BigDecimal totalAchievedAmount) { this.totalAchievedAmount = totalAchievedAmount; }
}
