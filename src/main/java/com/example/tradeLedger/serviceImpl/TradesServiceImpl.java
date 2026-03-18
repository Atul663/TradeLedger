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

    // ✅ SAVE SINGLE TRADE
    @Override
    public ResponseEntity<ResponseDto> addTodayTrade(TradesDTO tradesDTO, String email) {

        ResponseDto response = new ResponseDto();

        try {
            Trades trade = mapToEntity(tradesDTO);

            // (Optional) store email if your entity supports it
             trade.setEmail(email);

            tradesRepository.save(trade);

            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setMessage("Trade saved successfully");

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();

            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setMessage("Trade save failed");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ✅ SAVE MULTIPLE TRADES
    @Override
    public ResponseEntity<ResponseDto> saveAllTrade(List<TradesDTO> tradesDTOList) {

        ResponseDto response = new ResponseDto();
        List<Trades> allTrades = new ArrayList<>();

        try {
            for (TradesDTO dto : tradesDTOList) {
                allTrades.add(mapToEntity(dto));
            }

            tradesRepository.saveAll(allTrades);

            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setMessage("All trades saved successfully");

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();

            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setMessage("Bulk save failed");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ✅ GET TODAY TRADE
    @Override
    public ResponseEntity<ResponseDto> getTodayTrade(TradesDTO tradesDTO) {

        ResponseDto response = new ResponseDto();

        try {
            Trades todayTrade = tradesRepository.getTodayTrade(
                    tradesDTO.getDay(),
                    tradesDTO.getMonth(),
                    tradesDTO.getYear()
            );

            response.setData(todayTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();

            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something went wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ✅ GET MONTH TRADE
    @Override
    public ResponseEntity<ResponseDto> getMonthTrade(TradesDTO tradesDTO) {

        ResponseDto response = new ResponseDto();

        try {
            List<Trades> monthTrade = tradesRepository.getMonthTrade(
                    tradesDTO.getMonth(),
                    tradesDTO.getYear()
            );

            response.setData(monthTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();

            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something went wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ✅ GET YEAR TRADE
    @Override
    public ResponseEntity<ResponseDto> getYearTrade(TradesDTO tradesDTO) {

        ResponseDto response = new ResponseDto();

        try {
            List<Trades> yearTrade = tradesRepository.getYearTrade(
                    tradesDTO.getYear()
            );

            response.setData(yearTrade);
            response.setStatus(ApplicationConstants.SUCCESS_STATUS);
            response.setStatusCode(ApplicationConstants.SUCCESS_STATUS_CODE);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();

            response.setStatus(ApplicationConstants.FAILURE_STATUS);
            response.setStatusCode(ApplicationConstants.FAILURE_STATUS_CODE);
            response.setMessage("Something went wrong");

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 🔥 COMMON MAPPER (BEST PRACTICE)
    private Trades mapToEntity(TradesDTO dto) {

        Trades trade = new Trades();

        trade.setYear(dto.getYear());
        trade.setMonth(dto.getMonth());
        trade.setDay(dto.getDay());
        trade.setPnl(dto.getPnl());
        trade.setNoOfTrades(dto.getNoOfTrades());

        return trade;
    }
}