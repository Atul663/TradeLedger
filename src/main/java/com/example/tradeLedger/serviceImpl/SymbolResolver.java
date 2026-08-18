package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns the two ways a caller may name a market into one {@link Symbol}.
 *
 * Subscriptions and saved strategies both accept {@code symbolId}, or
 * {@code symbol} + {@code exchangeCode} - symbols are unique per exchange, never
 * globally, so a bare name is not an identifier.
 */
@Component
public class SymbolResolver {

    private final SymbolRepository symbolRepository;
    private final ExchangeRepository exchangeRepository;

    public SymbolResolver(SymbolRepository symbolRepository, ExchangeRepository exchangeRepository) {
        this.symbolRepository = symbolRepository;
        this.exchangeRepository = exchangeRepository;
    }

    /** @throws StrategyValidationException when nothing identifying was supplied */
    public Symbol require(UUID symbolId, String symbolName, String exchangeCode) {
        Symbol symbol = resolveOrNull(symbolId, symbolName, exchangeCode);
        if (symbol == null) {
            throw new StrategyValidationException("symbolId, or symbol + exchangeCode, is required");
        }
        return symbol;
    }

    /** All three absent means "not specified", which some callers allow. */
    public Symbol resolveOrNull(UUID symbolId, String symbolName, String exchangeCode) {
        Symbol symbol;
        if (symbolId != null) {
            symbol = symbolRepository.findById(symbolId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Symbol", symbolId));
        } else if (symbolName != null && !symbolName.isBlank()) {
            if (exchangeCode == null || exchangeCode.isBlank()) {
                throw new StrategyValidationException(
                        "exchangeCode is required when identifying a symbol by name - symbols are unique per exchange");
            }
            String code = exchangeCode.trim().toUpperCase(Locale.ROOT);
            Exchange exchange = exchangeRepository.findByCode(code)
                    .orElseThrow(() -> ResourceNotFoundException.of("Exchange", code));
            String name = symbolName.trim().toUpperCase(Locale.ROOT);
            symbol = symbolRepository.findByExchange_IdAndSymbol(exchange.getId(), name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Symbol", code + ":" + name));
        } else {
            return null;
        }
        if (!symbol.isActive()) {
            throw new StrategyValidationException("Symbol is not active: " + symbol.getSymbol());
        }
        return symbol;
    }
}
