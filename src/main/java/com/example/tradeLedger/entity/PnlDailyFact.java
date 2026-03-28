package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pnl_obligation_snapshot")
public class PnlDailyFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_day_id", nullable = false, unique = true)
    private PnlTradingDay tradingDay;

    @Column(name = "pay_in_pay_out", precision = 18, scale = 2)
    private BigDecimal payInPayOut;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal netPnl;

    @Column(precision = 18, scale = 2)
    private BigDecimal brokerage;

    @Column(name = "transaction_charges", precision = 18, scale = 2)
    private BigDecimal transactionCharges;

    @Column(name = "no_of_trades")
    private Integer noOfTrades;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "import_source_id")
    private PnlImportSource source;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PnlDailyFact() {}

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PnlTradingDay getTradingDay() { return tradingDay; }
    public void setTradingDay(PnlTradingDay tradingDay) { this.tradingDay = tradingDay; }

    public BigDecimal getPayInPayOut() { return payInPayOut; }
    public void setPayInPayOut(BigDecimal payInPayOut) { this.payInPayOut = payInPayOut; }

    public BigDecimal getNetPnl() { return netPnl; }
    public void setNetPnl(BigDecimal netPnl) { this.netPnl = netPnl; }

    public BigDecimal getBrokerage() { return brokerage; }
    public void setBrokerage(BigDecimal brokerage) { this.brokerage = brokerage; }

    public BigDecimal getTransactionCharges() { return transactionCharges; }
    public void setTransactionCharges(BigDecimal transactionCharges) { this.transactionCharges = transactionCharges; }

    public Integer getNoOfTrades() { return noOfTrades; }
    public void setNoOfTrades(Integer noOfTrades) { this.noOfTrades = noOfTrades; }

    public PnlImportSource getSource() { return source; }
    public void setSource(PnlImportSource source) { this.source = source; }

    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
