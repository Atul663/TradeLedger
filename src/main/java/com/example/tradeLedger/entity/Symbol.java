package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code symbols}: options-aware contract master.
 *
 * Convention from the design doc: indicators run on the UNDERLYING (spot/index),
 * so {@link StrategyInstance#getSymbol()} is the SIGNAL symbol. Orders target the
 * traded contract, which is an execution-plane concern and out of scope here.
 */
@Entity
@Table(name = "symbols",
        uniqueConstraints = @UniqueConstraint(name = "uq_symbols_exchange_symbol",
                columnNames = {"exchange_id", "symbol"}))
public class Symbol {

    public static final String TYPE_SPOT = "spot";
    public static final String TYPE_FUTURE = "future";
    public static final String TYPE_OPTION = "option";
    public static final String TYPE_INDEX = "index";

    public static final String OPTION_CALL = "CALL";
    public static final String OPTION_PUT = "PUT";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "base_asset", length = 20)
    private String baseAsset;

    @Column(name = "quote_asset", length = 20)
    private String quoteAsset;

    /** spot | future | option | index */
    @Column(name = "instrument_type", nullable = false, length = 20)
    private String instrumentType;

    /** CALL | PUT - required when instrumentType is 'option'. */
    @Column(name = "option_type", length = 4)
    private String optionType;

    @Column(name = "strike_price", precision = 20, scale = 8)
    private BigDecimal strikePrice;

    /** NULL = perpetual / non-expiring. */
    @Column(name = "expiry_at")
    private OffsetDateTime expiryAt;

    @Column(name = "contract_size", precision = 20, scale = 8)
    private BigDecimal contractSize;

    @Column(name = "tick_size", precision = 20, scale = 8)
    private BigDecimal tickSize;

    @Column(name = "min_qty", precision = 20, scale = 8)
    private BigDecimal minQty;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Exchange getExchange() { return exchange; }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getBaseAsset() { return baseAsset; }
    public void setBaseAsset(String baseAsset) { this.baseAsset = baseAsset; }

    public String getQuoteAsset() { return quoteAsset; }
    public void setQuoteAsset(String quoteAsset) { this.quoteAsset = quoteAsset; }

    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public String getOptionType() { return optionType; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public BigDecimal getStrikePrice() { return strikePrice; }
    public void setStrikePrice(BigDecimal strikePrice) { this.strikePrice = strikePrice; }

    public OffsetDateTime getExpiryAt() { return expiryAt; }
    public void setExpiryAt(OffsetDateTime expiryAt) { this.expiryAt = expiryAt; }

    public BigDecimal getContractSize() { return contractSize; }
    public void setContractSize(BigDecimal contractSize) { this.contractSize = contractSize; }

    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) { this.tickSize = tickSize; }

    public BigDecimal getMinQty() { return minQty; }
    public void setMinQty(BigDecimal minQty) { this.minQty = minQty; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
