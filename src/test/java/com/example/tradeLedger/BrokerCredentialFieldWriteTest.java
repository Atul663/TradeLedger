package com.example.tradeLedger;

import com.example.tradeLedger.dto.BrokerCredentialFieldRequest;
import com.example.tradeLedger.dto.BrokerCredentialFieldResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.BrokerCredentialField;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerCredentialFieldRepository;
import com.example.tradeLedger.repository.BrokerRepository;
import com.example.tradeLedger.serviceImpl.BrokerCredentialFieldServiceImpl;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write rules for the credential-form descriptor catalog.
 *
 * Two of them matter more than the rest. A descriptor whose {@code fieldKey} is
 * not a real {@code broker_credentials} column renders an input that binds to
 * nothing and fails at save time, so the key is checked against the same list the
 * table CHECKs. And a descriptor must never carry a credential: this table is
 * plaintext, so a default on a masked field would be a working secret in a
 * catalog every user can read. Both are pinned here, along with the
 * partial-update rule that judges the RESULTING row rather than the fragment
 * that arrived.
 */
class BrokerCredentialFieldWriteTest {

    private static final UUID ID = UUID.fromString("5c1f9e2a-1d3b-4c6e-9a80-2b7f4d6e8a10");
    private static final UUID BROKER_ID = UUID.fromString("8f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f");

    private BrokerCredentialFieldRepository repository;
    private BrokerRepository brokers;
    private BrokerCredentialFieldServiceImpl service;
    private Broker zerodha;

    @BeforeEach
    void setUp() {
        repository = mock(BrokerCredentialFieldRepository.class);
        brokers = mock(BrokerRepository.class);
        service = new BrokerCredentialFieldServiceImpl(repository, brokers,
                new JsonSupport(new ObjectMapper()));

        zerodha = new Broker();
        zerodha.setId(BROKER_ID);
        zerodha.setCode("ZERODHA");
        zerodha.setName("Zerodha");
        when(brokers.findById(BROKER_ID)).thenReturn(Optional.of(zerodha));
        when(brokers.findByCodeIgnoreCase("ZERODHA")).thenReturn(Optional.of(zerodha));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static BrokerCredentialFieldRequest request(String fieldKey, String dataType) {
        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setBrokerId(BROKER_ID);
        request.setFieldKey(fieldKey);
        request.setLabel("API Secret");
        request.setDataType(dataType);
        return request;
    }

    private BrokerCredentialField stored(String fieldKey, String dataType, String defaultValue) {
        BrokerCredentialField field = new BrokerCredentialField();
        field.setId(ID);
        field.setBroker(zerodha);
        field.setFieldKey(fieldKey);
        field.setLabel("API Key");
        field.setDataType(dataType);
        field.setDefaultValue(defaultValue);
        field.setFieldGroup(BrokerCredentialField.GROUP_CREDENTIALS);
        when(repository.findById(ID)).thenReturn(Optional.of(field));
        return field;
    }

    // --------------------------------------------------------------- the key

    @Test
    void aFieldKeyThatIsNotACredentialColumnIsRefused() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request("apikey", "text")));

        assertTrue(thrown.getMessage().contains("broker_credentials column"), thrown.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void aFieldKeyIsNormalizedToTheColumnSpelling() {
        BrokerCredentialFieldResponse created = service.create(request("  API_KEY  ", "text"));

        assertEquals("api_key", created.fieldKey());
        assertEquals("ZERODHA", created.brokerCode());
    }

    @Test
    void oneDescriptorPerColumnPerBroker() {
        when(repository.existsByBrokerIdAndFieldKeyIgnoreCase(BROKER_ID, "api_secret"))
                .thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> service.create(request("api_secret", "secret")));
        verify(repository, never()).save(any());
    }

    @Test
    void anUnknownBrokerIsNotFound() {
        BrokerCredentialFieldRequest request = request("api_key", "text");
        request.setBrokerId(null);
        request.setBrokerCode("NOPE");
        when(brokers.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(request));
    }

    @Test
    void aBrokerIsRequiredOnCreate() {
        BrokerCredentialFieldRequest request = request("api_key", "text");
        request.setBrokerId(null);

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));
        assertTrue(thrown.getMessage().contains("brokerId or brokerCode"), thrown.getMessage());
    }

    // ------------------------------------------------------- no credentials

    @Test
    void aSecretFieldCannotCarryADefault() {
        BrokerCredentialFieldRequest request = request("api_secret", "secret");
        request.setDefaultValue("a-real-looking-secret");

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));

        assertTrue(thrown.getMessage().contains("never carry a credential"), thrown.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void retypingToSecretIsRefusedWhileADefaultIsStillStored() {
        stored("redirect_url", BrokerCredentialField.TYPE_URL, "https://example.com/cb");

        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setDataType("secret");

        // The fragment carries no default at all - the stored one is what makes it
        // impossible, which is the point of judging the resulting row.
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.update(ID, request));
        assertTrue(thrown.getMessage().contains("never carry a credential"), thrown.getMessage());
    }

    @Test
    void clearingTheDefaultMakesTheSameRetypeWork() {
        stored("redirect_url", BrokerCredentialField.TYPE_URL, "https://example.com/cb");

        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setDataType("secret");
        request.setDefaultValue("");

        BrokerCredentialFieldResponse updated = service.update(ID, request);

        assertEquals("secret", updated.dataType());
        assertNull(updated.defaultValue());
    }

    // ------------------------------------------------------------- the rest

    @Test
    void aUrlDefaultHasToBeOneABrowserWillFollow() {
        BrokerCredentialFieldRequest request = request("redirect_url", "url");
        request.setDefaultValue("your-app.example.com/callback");

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));
        assertTrue(thrown.getMessage().contains("http://"), thrown.getMessage());
    }

    @Test
    void unsatisfiableBoundsAreRefused() {
        BrokerCredentialFieldRequest request = request("client_id", "text");
        request.setValidation(Map.of("minLength", 40, "maxLength", 10));

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));
        assertTrue(thrown.getMessage().contains("minLength must not exceed"), thrown.getMessage());
    }

    @Test
    void aBrokenPatternIsRefusedBeforeAFormEverCompilesIt() {
        BrokerCredentialFieldRequest request = request("totp_secret", "text");
        request.setValidation(Map.of("pattern", "^[A-Z"));

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));
        assertTrue(thrown.getMessage().contains("valid regular expression"), thrown.getMessage());
    }

    @Test
    void anUnknownGroupIsRefused() {
        BrokerCredentialFieldRequest request = request("api_key", "text");
        request.setFieldGroup("misc");

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));
        assertTrue(thrown.getMessage().contains("fieldGroup"), thrown.getMessage());
    }

    @Test
    void aNegativeDisplayOrderIsRefused() {
        BrokerCredentialFieldRequest request = request("api_key", "text");
        request.setDisplayOrder(-1);

        assertThrows(StrategyValidationException.class, () -> service.create(request));
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setBrokerId(BROKER_ID);
        request.setFieldKey("apikey");
        request.setDisplayOrder(-3);

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));

        assertEquals(3, thrown.getErrors().size(), thrown.getMessage());
    }

    @Test
    void defaultsAreTheOnesAFormExpects() {
        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setBrokerId(BROKER_ID);
        request.setFieldKey("api_key");
        request.setLabel("API Key");

        BrokerCredentialFieldResponse created = service.create(request);

        assertEquals("text", created.dataType());
        assertEquals("credentials", created.fieldGroup());
        assertEquals(0, created.displayOrder());
        assertTrue(created.required());
        assertTrue(created.userSupplied());
        assertTrue(created.active());
        assertTrue(created.validation().isEmpty());
    }

    @Test
    void anUpdateThatChangesNothingElseKeepsTheStoredValues() {
        stored("api_key", BrokerCredentialField.TYPE_TEXT, null);

        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setLabel("Kite API Key");

        BrokerCredentialFieldResponse updated = service.update(ID, request);

        assertEquals("Kite API Key", updated.label());
        assertEquals("api_key", updated.fieldKey());
        assertEquals("ZERODHA", updated.brokerCode());
        verify(repository, never()).existsByBrokerIdAndFieldKeyIgnoreCase(any(), anyString());
    }

    /**
     * The finder names resolve to real properties.
     *
     * A derived query is a string that is parsed at CONTEXT STARTUP, so a typo in
     * one - or a renamed field it walks through - is not a compile error and not
     * something the mocked tests above can see. Parsing them here with the same
     * parser Spring Data uses turns "the application will not boot" into a test
     * that fails in a second, without a database.
     */
    @Test
    void everyDerivedQueryResolvesAgainstTheEntity() {
        for (Method method : BrokerCredentialFieldRepository.class.getDeclaredMethods()) {
            assertDoesNotThrow(() -> new PartTree(method.getName(), BrokerCredentialField.class),
                    method.getName() + " does not resolve against BrokerCredentialField");
        }
    }

    @Test
    void rebindingToATakenKeyIsAConflict() {
        stored("api_key", BrokerCredentialField.TYPE_TEXT, null);
        when(repository.existsByBrokerIdAndFieldKeyIgnoreCase(eq(BROKER_ID), eq("api_secret")))
                .thenReturn(true);

        BrokerCredentialFieldRequest request = new BrokerCredentialFieldRequest();
        request.setFieldKey("api_secret");

        assertThrows(ResourceConflictException.class, () -> service.update(ID, request));
    }
}
