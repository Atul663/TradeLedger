package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.ResponseDto;
import com.example.tradeLedger.dto.TradesDTO;
import com.example.tradeLedger.entity.Trades;
import com.example.tradeLedger.service.TradesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trades")
public class TradesController {

    private final TradesService tradesService;

    public TradesController(TradesService tradesService) {
        this.tradesService = tradesService;
    }

    @PostMapping("/saveTrade")
    public ResponseEntity<ResponseDto> addTodayTrade(@RequestBody TradesDTO tradesDTO) {
        return tradesService.addTodayTrade(tradesDTO);
    }

    @PostMapping("/saveAllTrade")
    public ResponseEntity<ResponseDto> saveAllTrade(@RequestBody List<TradesDTO> tradesDTO) {
        return tradesService.saveAllTrade(tradesDTO);
    }

    @PostMapping("/getTodayTrade")
    public ResponseEntity<ResponseDto> getTodayTrade(@RequestBody TradesDTO tradesDTO) {
        return tradesService.getTodayTrade(tradesDTO);
    }

    @PostMapping("/getMonthTrade")
    public ResponseEntity<ResponseDto> getMonthTrade(@RequestBody TradesDTO tradesDTO) {
        return tradesService.getMonthTrade(tradesDTO);
    }

    @PostMapping("/getYearTrade")
    public ResponseEntity<ResponseDto> getYearTrade(@RequestBody TradesDTO tradesDTO) {
        return tradesService.getYearTrade(tradesDTO);
    }
}