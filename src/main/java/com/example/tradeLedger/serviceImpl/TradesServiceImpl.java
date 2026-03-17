package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.constant.ApplicationConstants;
import com.example.tradeLedger.dto.ResponseDto;
import com.example.tradeLedger.dto.TradesDTO;
import com.example.tradeLedger.entity.Trades;
import com.example.tradeLedger.repository.TradesRepository;
import com.example.tradeLedger.service.TradesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TradesServiceImpl implements TradesService {

    private final TradesRepository tradesRepository;

    public TradesServiceImpl(TradesRepository tradesRepository) {
        this.tradesRepository = tradesRepository;
    }

    @Override
    public ResponseEntity<ResponseDto> addTodayTrade(TradesDTO tradesDTO) {
        ResponseDto response = new ResponseDto();
        try {
            Trades trade = new Trades();
            trade.setYear(tradesDTO.getYear());
            trade.setMonth(tradesDTO.getMonth());
            trade.setDay(tradesDTO.getDay());
            trade.setPnl(tradesDTO.getPnl());
            trade.setNoOfTrades(tradesDTO.getNoOfTrades());

            tradesRepository.save(trade);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setMessage("Trade save successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setMessage("Trade save failed");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getTodayTrade(TradesDTO tradesDTO) {
        ResponseDto response = new ResponseDto();
        try {
            Trades todayTrade = tradesRepository.getTodayTrade(tradesDTO.getDay(), tradesDTO.getMonth(),tradesDTO.getYear());

            response.setData(todayTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something is wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getMonthTrade(TradesDTO tradesDTO) {
        ResponseDto response = new ResponseDto();
        try {
            List<Trades> monthTrade = tradesRepository.getMonthTrade(tradesDTO.getMonth(),tradesDTO.getYear());

            response.setData(monthTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something is wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getYearTrade(TradesDTO tradesDTO) {
        ResponseDto response = new ResponseDto();
        try {
            List<Trades> monthTrade = tradesRepository.getYearTrade(tradesDTO.getYear());

            response.setData(monthTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something is wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> saveAllTrade(List<TradesDTO> tradesDTO) {
        ResponseDto response = new ResponseDto();
        List<Trades> allTrades = new ArrayList<>();
        try {
            for(TradesDTO trades : tradesDTO) {
                Trades trade = new Trades();
                trade.setYear(trade.getYear());
                trade.setMonth(trade.getMonth());
                trade.setDay(trade.getDay());
                trade.setPnl(trade.getPnl());
                trade.setNoOfTrades(trade.getNoOfTrades());

                allTrades.add(trade);
            }

            tradesRepository.saveAll(allTrades);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setMessage("Trade save successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setMessage("Trade save failed");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

// test123