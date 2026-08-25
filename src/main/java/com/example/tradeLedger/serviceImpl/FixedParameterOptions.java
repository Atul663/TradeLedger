package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.utils.JsonSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills in the choices a descriptor cannot store for itself.
 *
 * Most knobs know their own options: an {@code enum} carries them in
 * {@code validation.options}, authored once and true forever. A
 * {@link FixedParameter#TYPE_SYMBOL} knob cannot, because its choices are ROWS -
 * list an instrument and the option set changed, retire one and it changed
 * again. A copy in {@code fixed_parameters} would be stale the same day.
 *
 * So the descriptor declares its TYPE and this fills the options on the way out,
 * from the table that owns them. Every read path that serves a descriptor goes
 * through here, so the catalog endpoints and the strategy form cannot end up
 * offering different lists.
 *
 * Nothing is written back: {@code validation} in the database stays exactly what
 * an admin authored, which for a symbol knob is nothing at all.
 */
@Component
public class FixedParameterOptions {

    public static final String KEY_OPTIONS = "options";

    /**
     * Where the options came from, so a client can refresh the list without
     * knowing which knob names are reference types.
     */
    public static final String KEY_OPTIONS_SOURCE = "optionsSource";

    private static final String SYMBOLS_SOURCE = "/api/v1/symbols";
    private static final String EXCHANGES_SOURCE = "/api/v1/exchanges";

    private final SymbolRepository symbolRepository;
    private final ExchangeRepository exchangeRepository;
    private final JsonSupport json;

    public FixedParameterOptions(SymbolRepository symbolRepository,
                                 ExchangeRepository exchangeRepository,
                                 JsonSupport json) {
        this.symbolRepository = symbolRepository;
        this.exchangeRepository = exchangeRepository;
        this.json = json;
    }

    /**
     * The descriptor's stored bounds, with the options filled in where the type
     * says they live in a table.
     *
     * @return a fresh map, empty for an unbounded knob - never null
     */
    public Map<String, Object> validation(FixedParameter parameter) {
        return snapshot().validation(parameter);
    }

    /**
     * One reusable read of the reference tables, for a response that describes the
     * same knobs many times over.
     *
     * A list of fifty strategies renders the SAME symbol and exchange lists fifty
     * times; asking the database each time is fifty round trips for one answer
     * that cannot change inside a transaction. A snapshot reads each table at most
     * once and answers from memory after that.
     *
     * <b>Lazy per table.</b> Building one costs nothing - a response with no
     * reference knob in it never touches either table. Hold one for the length of
     * a request and no longer: it is a cache with the lifetime of the answer it
     * serves, which is what keeps the options as fresh as the request that asked.
     */
    public Snapshot snapshot() {
        return new Snapshot();
    }

    public final class Snapshot {

        private List<String> symbols;
        private List<String> exchanges;

        private Snapshot() {
        }

        /** The descriptor's stored bounds, with table-backed options filled in. */
        public Map<String, Object> validation(FixedParameter parameter) {
            Map<String, Object> rules = new LinkedHashMap<>(json.toMap(parameter.getValidation()));
            if (FixedParameter.TYPE_SYMBOL.equals(parameter.getDataType())) {
                if (symbols == null) {
                    symbols = symbolOptions();
                }
                rules.put(KEY_OPTIONS, symbols);
                rules.put(KEY_OPTIONS_SOURCE, SYMBOLS_SOURCE);
            } else if (FixedParameter.TYPE_EXCHANGE.equals(parameter.getDataType())) {
                if (exchanges == null) {
                    exchanges = exchangeOptions();
                }
                rules.put(KEY_OPTIONS, exchanges);
                rules.put(KEY_OPTIONS_SOURCE, EXCHANGES_SOURCE);
            }
            return rules;
        }
    }

    /**
     * The tickers a strategy may name, from the active symbols.
     *
     * Tickers rather than ids, because {@code symbol} is the field a PUT carries
     * and the value this knob reports - a select whose options do not match the
     * field's own vocabulary is not a select for that field.
     *
     * <b>Deduplicated.</b> A ticker is unique per EXCHANGE, not globally, so the
     * same one may sit on two venues; it is offered once, and {@code exchangeCode}
     * alongside it picks the venue. That is the same pair
     * {@code UserStrategyRequest} takes, and the same one {@link SymbolResolver}
     * requires - a bare ticker was never an identifier here.
     */
    private List<String> symbolOptions() {
        Set<String> tickers = new LinkedHashSet<>();
        for (Symbol symbol : symbolRepository.findByActiveTrueOrderBySymbolAsc()) {
            tickers.add(symbol.getSymbol());
        }
        return new ArrayList<>(tickers);
    }

    /**
     * The venues a strategy may name, from the active exchanges.
     *
     * Codes rather than names or ids, because {@code exchangeCode} is the field a
     * PUT carries - 'NSE', not 'National Stock Exchange of India'. A disabled
     * exchange is left out: {@link SymbolResolver} would refuse a symbol on one
     * anyway, so offering it would be offering a choice that cannot be saved.
     */
    private List<String> exchangeOptions() {
        List<String> codes = new ArrayList<>();
        for (Exchange exchange
                : exchangeRepository.findByStatusOrderByNameAsc(Exchange.STATUS_ACTIVE)) {
            codes.add(exchange.getCode());
        }
        return codes;
    }
}
