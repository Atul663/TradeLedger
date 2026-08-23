package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Create / update a symbol - the contract a strategy watches.
 *
 * PUT is partial: an absent field keeps its stored value, and the exchange cannot
 * be changed once set.
 */
@Schema(name = "SymbolRequest",
        description = """
                A tradable instrument. SHARED platform master data - no owner column, so anything \
                written here is visible to every user.

                A strategy points at the UNDERLYING it watches, so for the option strategies this \
                platform runs that is normally an instrumentType of `index` (NIFTY, BANKNIFTY) or \
                `spot` (a stock). The sheet's INDEX-vs-STOCK cell IS this field - there is no \
                separate column for it. Which contract an order lands on is decided by the \
                strategy's derivative and CE/PE columns at entry time, not here.

                UNIQUE per (exchange, symbol), and the ticker is uppercased on save. \
                optionType and strikePrice apply only when instrumentType is `option`.""")
public class SymbolRequest {

    @Schema(description = "The venue. Send this or exchangeCode. Cannot be changed on update.",
            example = "9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b")
    private UUID exchangeId;

    @Schema(description = "Alternative to exchangeId - exchanges.code is unique.", example = "NSE")
    private String exchangeCode;

    @Schema(description = "The ticker, uppercased on save. UNIQUE within the exchange.",
            example = "NIFTY", maxLength = 50)
    private String symbol;

    @Schema(description = "What is being traded.", example = "NIFTY", maxLength = 20)
    private String baseAsset;

    @Schema(description = "What it is priced in.", example = "INR", maxLength = 20)
    private String quoteAsset;

    @Schema(description = "Use index or spot for a strategy's underlying.",
            example = "index", allowableValues = {"spot", "future", "option", "index"})
    private String instrumentType;

    @Schema(description = "Required when instrumentType is option, and rejected otherwise.",
            example = "null", allowableValues = {"CALL", "PUT"})
    private String optionType;

    @Schema(description = "Options only.", example = "null")
    private BigDecimal strikePrice;

    @Schema(description = "Null means perpetual or non-expiring - which is what an index "
            + "underlying is.", example = "null")
    private OffsetDateTime expiryAt;

    @Schema(description = "The exchange lot size. Set it from the current contract "
            + "specification - lot sizes change.", example = "75", minimum = "0")
    private BigDecimal contractSize;

    @Schema(description = "The minimum price increment.", example = "0.05", minimum = "0")
    private BigDecimal tickSize;

    @Schema(description = "The minimum order quantity.", example = "75", minimum = "0")
    private BigDecimal minQty;

    @Schema(description = "An inactive symbol is refused when a strategy tries to pick it. "
            + "Deactivate an expired contract rather than deleting it.",
            example = "true", defaultValue = "true")
    private Boolean active;

    public UUID getExchangeId() { return exchangeId; }
    public void setExchangeId(UUID exchangeId) { this.exchangeId = exchangeId; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

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

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
