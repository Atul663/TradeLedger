package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.ResponseDto;
import com.example.tradeLedger.dto.TradesDTO;
import com.example.tradeLedger.entity.Trades;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface TradesService {

    ResponseEntity<ResponseDto> addTodayTrade(TradesDTO tradesDTO);

    ResponseEntity<ResponseDto> getTodayTrade(TradesDTO tradesDTO);

    ResponseEntity<ResponseDto> getMonthTrade(TradesDTO tradesDTO);

    ResponseEntity<ResponseDto> getYearTrade(TradesDTO tradesDTO);

    ResponseEntity<ResponseDto> saveAllTrade(List<TradesDTO> tradesDTO);
}