package com.example.tradeLedger.controller;

import com.example.tradeLedger.constant.ApplicationConstants;
import com.example.tradeLedger.dto.PnlManualEntryRequestDto;
import com.example.tradeLedger.dto.PnlMonthTargetUpdateDto;
import com.example.tradeLedger.dto.PnlPlanRequestDto;
import com.example.tradeLedger.dto.ResponseDto;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.PnlLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pnl")
public class PnlController {

    private final PnlLedgerService pnlLedgerService;
    private final UserDetailsRepository userDetailsRepository;

    public PnlController(PnlLedgerService pnlLedgerService, UserDetailsRepository userDetailsRepository) {
        this.pnlLedgerService = pnlLedgerService;
        this.userDetailsRepository = userDetailsRepository;
    }

    @PostMapping("/plans")
    public ResponseEntity<ResponseDto> savePlan(@RequestBody PnlPlanRequestDto request, Authentication authentication) {
        return execute(authentication, "Plan saved successfully", user -> pnlLedgerService.savePlan(user, request));
    }

    @GetMapping("/plans")
    public ResponseEntity<ResponseDto> getPlans(Authentication authentication) {
        return execute(authentication, "Plans fetched successfully", pnlLedgerService::getPlans);
    }

    @GetMapping("/plans/active")
    public ResponseEntity<ResponseDto> getActivePlan(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "FNO") String planType
    ) {
        return execute(authentication, "Active plan fetched successfully", user -> pnlLedgerService.getActivePlan(user, tradeDate, planType));
    }

    @PatchMapping("/plans/months/{monthId}/manual-target")
    public ResponseEntity<ResponseDto> updateManualTarget(
            @PathVariable Long monthId,
            @RequestBody PnlMonthTargetUpdateDto request,
            Authentication authentication
    ) {
        return execute(authentication, "Month target updated successfully", user -> pnlLedgerService.updateMonthTarget(user, monthId, request));
    }

    @PostMapping("/daily/manual-entry")
    public ResponseEntity<ResponseDto> upsertManualDailyPnl(
            @RequestBody PnlManualEntryRequestDto request,
            Authentication authentication
    ) {
        return execute(authentication, "Daily PnL saved successfully", user -> pnlLedgerService.upsertManualDailyPnl(user, request));
    }

    private ResponseEntity<ResponseDto> execute(
            Authentication authentication,
            String successMessage,
            PnlUserOperation operation
    ) {
        ResponseDto response = new ResponseDto();

        try {
            UserDetails user = resolveUser(authentication);
            Object data = operation.apply(user);

            response.setData(data);
            response.setMessage(successMessage);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            response.setMessage(ex.getMessage());
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            response.setMessage(ex.getMessage());
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private UserDetails resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        String email = ((String) authentication.getPrincipal()).trim().toLowerCase();
        return userDetailsRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
    }

    @FunctionalInterface
    private interface PnlUserOperation {
        Object apply(UserDetails user);
    }
}
