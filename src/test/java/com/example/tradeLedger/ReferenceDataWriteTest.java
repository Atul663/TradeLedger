package com.example.tradeLedger;

import com.example.tradeLedger.dto.ExchangeRequest;
import com.example.tradeLedger.dto.RiskProfileRequest;
import com.example.tradeLedger.dto.SymbolRequest;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.RiskProfileRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.repository.UserRiskLimitRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.serviceImpl.ReferenceDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write rules for the three shared catalogs.
 *
 * These tables have no owner column, so anything written here is visible to every
 * user and a bad delete would take a symbol away from everyone watching it. The
 * protection is structural rather than role-based - a delete is refused while
 * anything references the row - and that is what these pin down, along with the
 * field rules a form should mirror.
 */
class ReferenceDataWriteTest {

    private ExchangeRepository exchanges;
    private SymbolRepository symbols;
    private RiskProfileRepository riskProfiles;
    private UserStrategyRepository userStrategies;
    private StrategySubscriptionRepository subscriptions;
    private ReferenceDataServiceImpl service;

    private static final UUID EXCHANGE_ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b");

    @BeforeEach
    void setUp() {
        exchanges = mock(ExchangeRepository.class);
        symbols = mock(SymbolRepository.class);
        riskProfiles = mock(RiskProfileRepository.class);
        userStrategies = mock(UserStrategyRepository.class);
        subscriptions = mock(StrategySubscriptionRepository.class);
        service = new ReferenceDataServiceImpl(exchanges, symbols, riskProfiles,
                mock(UserRiskLimitRepository.class), userStrategies, subscriptions,
                mock(CurrentUserService.class));
    }

    private static Exchange nse() {
        Exchange exchange = new Exchange();
        exchange.setId(EXCHANGE_ID);
        exchange.setName("National Stock Exchange of India");
        exchange.setCode("NSE");
        exchange.setStatus(Exchange.STATUS_ACTIVE);
        return exchange;
    }

    // ------------------------------------------------------------ exchanges

    @Test
    void createsAnExchangeAndUppercasesItsCode() {
        ExchangeRequest request = new ExchangeRequest();
        request.setName("National Stock Exchange of India");
        request.setCode(" nse ");
        when(exchanges.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createExchange(request);

        ArgumentCaptor<Exchange> saved = ArgumentCaptor.forClass(Exchange.class);
        verify(exchanges).save(saved.capture());
        assertEquals("NSE", saved.getValue().getCode(), "code is matched by exact string");
        assertEquals(Exchange.STATUS_ACTIVE, saved.getValue().getStatus());
    }

    @Test
    void rejectsAnExchangeWithNoCode() {
        ExchangeRequest request = new ExchangeRequest();
        request.setName("Some venue");

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createExchange(request));
        assertTrue(thrown.getMessage().contains("code"), thrown.getMessage());
    }

    @Test
    void rejectsAnUnknownExchangeStatus() {
        ExchangeRequest request = new ExchangeRequest();
        request.setName("Some venue");
        request.setCode("XXX");
        request.setStatus("paused");

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createExchange(request));
        assertTrue(thrown.getMessage().contains("status"), thrown.getMessage());
    }

    @Test
    void refusesADuplicateExchangeCode() {
        ExchangeRequest request = new ExchangeRequest();
        request.setName("Another NSE");
        request.setCode("NSE");
        when(exchanges.existsByCode("NSE")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.createExchange(request));
        verify(exchanges, never()).save(any());
    }

    /** The code is what clients resolve symbols by, so it cannot move under them. */
    @Test
    void refusesToRecodeAnExchangeThatHasSymbols() {
        ExchangeRequest request = new ExchangeRequest();
        request.setCode("NSE2");
        when(exchanges.findById(EXCHANGE_ID)).thenReturn(Optional.of(nse()));
        when(symbols.countByExchange_Id(EXCHANGE_ID)).thenReturn(3L);

        ResourceConflictException thrown = assertThrows(ResourceConflictException.class,
                () -> service.updateExchange(EXCHANGE_ID, request));
        assertTrue(thrown.getMessage().contains("3 symbol"), thrown.getMessage());
    }

    @Test
    void refusesToDeleteAnExchangeThatStillHasSymbols() {
        when(exchanges.findById(EXCHANGE_ID)).thenReturn(Optional.of(nse()));
        when(symbols.countByExchange_Id(EXCHANGE_ID)).thenReturn(2L);

        ResourceConflictException thrown = assertThrows(ResourceConflictException.class,
                () -> service.deleteExchange(EXCHANGE_ID));
        assertTrue(thrown.getMessage().contains("Disable it instead"), thrown.getMessage());
        verify(exchanges, never()).delete(any());
    }

    // -------------------------------------------------------------- symbols

    @Test
    void createsAnIndexUnderlyingByExchangeCode() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("nse");
        request.setSymbol(" nifty ");
        request.setInstrumentType("INDEX");
        request.setContractSize(new BigDecimal("75"));
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));
        when(symbols.findByExchange_IdAndSymbol(EXCHANGE_ID, "NIFTY")).thenReturn(Optional.empty());
        when(symbols.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSymbol(request);

        ArgumentCaptor<Symbol> saved = ArgumentCaptor.forClass(Symbol.class);
        verify(symbols).save(saved.capture());
        assertEquals("NIFTY", saved.getValue().getSymbol());
        assertEquals("index", saved.getValue().getInstrumentType(), "types are stored lowercase");
        assertNull(saved.getValue().getOptionType(), "an index has no option side");
        assertTrue(saved.getValue().isActive());
    }

    @Test
    void rejectsAnUnknownInstrumentType() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setSymbol("NIFTY");
        request.setInstrumentType("perpetual");
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createSymbol(request));
        assertTrue(thrown.getMessage().contains("instrumentType"), thrown.getMessage());
    }

    /** An option without a side and a strike is not a contract anyone can trade. */
    @Test
    void requiresASideAndAStrikeOnAnOption() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setSymbol("NIFTY25000CE");
        request.setInstrumentType("option");
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createSymbol(request));
        assertEquals(2, thrown.getErrors().size(), thrown.getErrors().toString());
        assertTrue(thrown.getMessage().contains("optionType"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("strikePrice"), thrown.getMessage());
    }

    @Test
    void rejectsAnOptionSideOnANonOption() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setSymbol("NIFTY");
        request.setInstrumentType("index");
        request.setOptionType("CALL");
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createSymbol(request));
        assertTrue(thrown.getMessage().contains("only applies when instrumentType is option"),
                thrown.getMessage());
    }

    @Test
    void rejectsANonPositiveLotSize() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setSymbol("NIFTY");
        request.setInstrumentType("index");
        request.setContractSize(BigDecimal.ZERO);
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createSymbol(request));
        assertTrue(thrown.getMessage().contains("contractSize"), thrown.getMessage());
    }

    @Test
    void refusesADuplicateTickerOnTheSameExchange() {
        Symbol existing = new Symbol();
        existing.setId(UUID.randomUUID());
        existing.setSymbol("NIFTY");
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setSymbol("NIFTY");
        request.setInstrumentType("index");
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));
        when(symbols.findByExchange_IdAndSymbol(EXCHANGE_ID, "NIFTY")).thenReturn(Optional.of(existing));

        assertThrows(ResourceConflictException.class, () -> service.createSymbol(request));
        verify(symbols, never()).save(any());
    }

    @Test
    void refusesToDeleteASymbolAStrategyWatches() {
        UUID symbolId = UUID.randomUUID();
        Symbol symbol = new Symbol();
        symbol.setId(symbolId);
        symbol.setSymbol("NIFTY");
        symbol.setExchange(nse());
        when(symbols.findById(symbolId)).thenReturn(Optional.of(symbol));
        when(userStrategies.countBySymbol_Id(symbolId)).thenReturn(4L);

        ResourceConflictException thrown = assertThrows(ResourceConflictException.class,
                () -> service.deleteSymbol(symbolId));
        assertTrue(thrown.getMessage().contains("4 strategy"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Deactivate it instead"), thrown.getMessage());
        verify(symbols, never()).delete(any());
    }

    // -------------------------------------------------------- risk profiles

    @Test
    void createsARiskProfileWithTheKillSwitchOnByDefault() {
        RiskProfileRequest request = new RiskProfileRequest();
        request.setName("Conservative");
        request.setMaxDailyLoss(new BigDecimal("5000"));
        when(riskProfiles.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createRiskProfile(request);

        ArgumentCaptor<com.example.tradeLedger.entity.RiskProfile> saved =
                ArgumentCaptor.forClass(com.example.tradeLedger.entity.RiskProfile.class);
        verify(riskProfiles).save(saved.capture());
        assertTrue(saved.getValue().isKillSwitchEnabled());
        assertNull(saved.getValue().getMaxDrawdown(), "an absent cap means uncapped");
    }

    @Test
    void rejectsNegativeCaps() {
        RiskProfileRequest request = new RiskProfileRequest();
        request.setName("Broken");
        request.setMaxDailyLoss(new BigDecimal("-1"));
        request.setMaxTradesPerDay(0);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createRiskProfile(request));
        assertEquals(2, thrown.getErrors().size(), thrown.getErrors().toString());
    }

    @Test
    void refusesToDeleteARiskProfileInUse() {
        UUID id = UUID.randomUUID();
        com.example.tradeLedger.entity.RiskProfile profile =
                new com.example.tradeLedger.entity.RiskProfile();
        profile.setId(id);
        profile.setName("Conservative");
        when(riskProfiles.findById(id)).thenReturn(Optional.of(profile));
        when(subscriptions.existsByRiskProfile_Id(id)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.deleteRiskProfile(id));
        verify(riskProfiles, never()).delete(any());
    }

    @Test
    void deletesARiskProfileNobodyUses() {
        UUID id = UUID.randomUUID();
        com.example.tradeLedger.entity.RiskProfile profile =
                new com.example.tradeLedger.entity.RiskProfile();
        profile.setId(id);
        profile.setName("Unused");
        when(riskProfiles.findById(id)).thenReturn(Optional.of(profile));
        when(subscriptions.existsByRiskProfile_Id(id)).thenReturn(false);

        service.deleteRiskProfile(id);

        verify(riskProfiles).delete(profile);
    }

    /** A form with several mistakes should report them all in one round trip. */
    @Test
    void reportsEveryProblemAtOnce() {
        SymbolRequest request = new SymbolRequest();
        request.setExchangeCode("NSE");
        request.setInstrumentType("option");
        request.setTickSize(new BigDecimal("-1"));
        when(exchanges.findByCode("NSE")).thenReturn(Optional.of(nse()));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> service.createSymbol(request));
        List<String> errors = thrown.getErrors();
        assertTrue(errors.size() >= 4, errors.toString());
    }
}
