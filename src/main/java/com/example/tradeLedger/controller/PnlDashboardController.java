package com.example.tradeLedger.controller;

import com.example.tradeLedger.constant.ApplicationConstants;
import com.example.tradeLedger.dto.ResponseDto;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.PnlDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pnl/dashboard")
public class PnlDashboardController {

    private final PnlDashboardService pnlDashboardService;
    private final UserDetailsRepository userDetailsRepository;

    public PnlDashboardController(PnlDashboardService pnlDashboardService, UserDetailsRepository userDetailsRepository) {
        this.pnlDashboardService = pnlDashboardService;
        this.userDetailsRepository = userDetailsRepository;
    }

    @GetMapping
    public ResponseEntity<ResponseDto> getDashboard(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "FNO") String planType
    ) {
        return execute(authentication, "Dashboard fetched successfully", user -> pnlDashboardService.getWorkbookView(user, tradeDate, planType));
    }

    @GetMapping("/current-month-summary")
    public ResponseEntity<ResponseDto> getCurrentMonthSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "FNO") String planType
    ) {
        return execute(authentication, "Current month summary fetched successfully", user -> pnlDashboardService.getCurrentMonthSummary(user, tradeDate, planType));
    }

    @GetMapping("/year-summary")
    public ResponseEntity<ResponseDto> getYearSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "FNO") String planType
    ) {
        return execute(authentication, "Year summary fetched successfully", user -> pnlDashboardService.getYearSummary(user, tradeDate, planType));
    }

    @GetMapping("/month-sheet")
    public ResponseEntity<ResponseDto> getMonthSheet(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "FNO") String planType
    ) {
        return execute(authentication, "Month sheet fetched successfully", user -> pnlDashboardService.getMonthSheet(user, tradeDate, planType));
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
