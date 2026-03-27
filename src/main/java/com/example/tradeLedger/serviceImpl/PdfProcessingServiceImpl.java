package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.AnnexureDto;
import com.example.tradeLedger.dto.ObligationDto;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.service.GeminiService;
import com.example.tradeLedger.service.PnlLedgerService;
import com.example.tradeLedger.service.PdfProcessingService;
import com.example.tradeLedger.service.PdfService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfProcessingServiceImpl implements PdfProcessingService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("d-M-uu"));

    private final PdfService pdfService;
    private final GeminiService geminiService;
    private final PnlLedgerService pnlLedgerService;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public PdfProcessingServiceImpl(PdfService pdfService,
            GeminiService geminiService,
            PnlLedgerService pnlLedgerService) {
        this.pdfService = pdfService;
        this.geminiService = geminiService;
        this.pnlLedgerService = pnlLedgerService;
    }

    @Override
    public String processPdf(String filePath, String password) throws Exception {
        return processPdf(filePath, password, null, null, null);
    }

    @Override
    public String processPdf(String filePath, String password, UserDetails user, String gmailMessageId,
            String attachmentChecksum) throws Exception {

        // 1️⃣ Extract full text
        String fullText = pdfService.extractText(filePath, password);

        // 🔥 2️⃣ Extract sections
        String obligationText = pdfService.extractObligationSection(fullText);
        String annexureText = pdfService.extractAnnexureSection(fullText);

        // 🔥 3️⃣ Parse using Java
        ObligationDto obligation = parseObligation(obligationText);
        List<AnnexureDto> annexureList = parseAnnexure(annexureText);
        LocalDate tradeDate = extractTradeDate(fullText, filePath);

        // 🔥 4️⃣ Fallback to Gemini (ONLY if needed)
        // if (annexureList.isEmpty()) {
        // System.out.println("GEN AI IS USE");
        // String aiResult = geminiService.extractData(fullText);
        //
        // String cleanJson = extractJsonFromGeminiResponse(aiResult);
        //
        // return cleanJson;
        // }

        // 🔥 5️⃣ Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeDate", tradeDate);
        result.put("obligation", obligation);
        result.put("annexure", annexureList);
        result.put("annexureCount", annexureList.size());

        String lowerPath = filePath.toLowerCase();
        String planType = "EQUITY";
        if (lowerPath.contains("fon")) {
            planType = "FNO";
        } else if (lowerPath.contains("stock")) {
            planType = "STOCK";
        } else if (lowerPath.contains("crypto")) {
            planType = "CRYPTO";
        }

        System.out.println("ob ; " + obligationText);
        System.out.println("ob ; " + obligation);
        if (user != null) {
            result.put(
                    "ledger",
                    pnlLedgerService.saveObligationAndCalculate(
                            user,
                            tradeDate,
                            obligation,
                            annexureList.size(),
                            gmailMessageId,
                            attachmentChecksum,
                            planType));
        }

        System.out.println("annexureList length" + annexureList.size());
        return objectMapper.writeValueAsString(result);
    }

    private String normalize(String line) {
        return line
                .toLowerCase()
                .replaceAll("[^a-z0-9.\\- ]", "") // remove symbols
                .replaceAll("\\s+", " ") // normalize spaces
                .trim();
    }

    private ObligationDto parseObligation(String text) {

        ObligationDto dto = new ObligationDto();

        try {
            String[] lines = text.split("\n");

            for (String line : lines) {

                String normalized = normalize(line);

                if (normalized.isEmpty())
                    continue;

                // ✅ PAY IN / PAY OUT
                if (normalized.contains("pay in") && normalized.contains("pay out")) {
                    dto.setPayInPayOut(extractAmount(line));
                }

                // ✅ BROKERAGE
                else if (normalized.startsWith("brokerage charges")) {
                    dto.setBrokerage(extractAmount(line));
                }

                // ✅ NET AMOUNT
                else if (normalized.contains("net amount")) {
                    dto.setNetAmount(extractAmount(line));
                }

                else if (normalized.contains("transaction charges")) {
                    dto.setTransactionCharge(extractAmount(line));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return dto;
    }

    private List<AnnexureDto> parseAnnexure(String text) {

        List<AnnexureDto> list = new ArrayList<>();

        try {

            String[] lines = text.split("\n");

            for (String line : lines) {

                line = line.trim();

                if (line.isEmpty())
                    continue;

                // 🔥 Skip headers & unwanted
                if (line.toLowerCase().contains("order")
                        || line.toLowerCase().contains("annexure")
                        || line.toLowerCase().contains("remarks")
                        || line.toLowerCase().contains("brokerage charges")
                        || line.length() < 30) {
                    continue;
                }

                // 🔥 Identify valid trade row
                if (line.matches("^\\d+.*") &&
                        (line.contains("OPTIDX") || line.contains("OPTSTK") || line.contains("FUT"))) {

                    AnnexureDto dto = parseTradeRow(line);

                    if (dto != null) {
                        list.add(dto);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private AnnexureDto parseTradeRow(String line) {

        try {

            line = line.replace(",", "");
            String[] parts = line.split("\\s+");

            // 🔥 Find contract start
            int contractIndex = -1;

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith("OPT") || parts[i].startsWith("FUT")) {
                    contractIndex = i;
                    break;
                }
            }

            if (contractIndex == -1)
                return null;

            AnnexureDto dto = new AnnexureDto();

            // 🔥 Build contract dynamically
            StringBuilder contractBuilder = new StringBuilder();
            int i = contractIndex;

            while (i < parts.length && !parts[i].equals("B") && !parts[i].equals("S")) {
                contractBuilder.append(parts[i]).append(" ");
                i++;
            }

            dto.setContract(contractBuilder.toString().trim());

            // 🔥 Now i is at B/S
            String buySell = parts[i];
            dto.setBuySell(buySell);

            // 🔥 Quantity
            int quantity = (int) Double.parseDouble(parts[i + 1]);
            dto.setQuantity(quantity);

            // 🔥 WAP
            double wap = Double.parseDouble(parts[i + 2]);
            dto.setWap(wap);

            // 🔥 Net total → last numeric value
            double netTotal = 0;

            for (int j = parts.length - 1; j >= 0; j--) {
                if (parts[j].matches("-?\\d+\\.\\d+")) {
                    netTotal = Double.parseDouble(parts[j]);
                    break;
                }
            }

            dto.setNetTotal(netTotal);

            return dto;

        } catch (Exception e) {
            return null;
        }
    }

    private double extractAmount(String line) {

        try {
            // Remove commas
            line = line.replace(",", "");

            // Regex to extract last number
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?");
            java.util.regex.Matcher matcher = pattern.matcher(line);

            double lastNumber = 0;

            while (matcher.find()) {
                lastNumber = Double.parseDouble(matcher.group());
            }

            return lastNumber;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private LocalDate extractTradeDate(String fullText, String filePath) {
        List<Pattern> textPatterns = List.of(
                Pattern.compile(
                        "(?i)(trade\\s*date|trading\\s*date|statement\\s*date|bill\\s*date|settlement\\s*date)\\s*[:\\-]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"),
                Pattern.compile(
                        "(?i)(trade\\s*date|trading\\s*date|statement\\s*date|bill\\s*date|settlement\\s*date)\\s*[:\\-]?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})"));

        for (Pattern pattern : textPatterns) {
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                LocalDate parsedDate = parseDateToken(matcher.group(2));
                if (parsedDate != null) {
                    return parsedDate;
                }
            }
        }

        String fileName = Paths.get(filePath).getFileName().toString();
        List<Pattern> fileNamePatterns = List.of(
                Pattern.compile("(\\d{1,2}[._-]\\d{1,2}[._-]\\d{2,4})"),
                Pattern.compile("(\\d{8})"));

        for (Pattern pattern : fileNamePatterns) {
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()) {
                LocalDate parsedDate = parseDateToken(matcher.group(1));
                if (parsedDate != null) {
                    return parsedDate;
                }
            }
        }

        throw new IllegalArgumentException("Could not determine trade date from the statement text or filename.");
    }

    private LocalDate parseDateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }

        String normalized = rawToken.trim().replace('.', '-').replace('_', '-').replaceAll("\\s+", " ");

        if (normalized.matches("\\d{8}")) {
            normalized = normalized.substring(0, 2) + "-" + normalized.substring(2, 4) + "-" + normalized.substring(4);
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate parsedDate = LocalDate.parse(normalized, formatter);
                if (parsedDate.getYear() < 100) {
                    parsedDate = parsedDate.plusYears(2000 - parsedDate.getYear());
                }
                return parsedDate;
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private String extractJsonFromGeminiResponse(String response) {

        try {
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> map = mapper.readValue(response, Map.class);

            List candidates = (List) map.get("candidates");

            if (candidates != null && !candidates.isEmpty()) {

                Map first = (Map) candidates.get(0);
                Map content = (Map) first.get("content");

                List parts = (List) content.get("parts");

                if (parts != null && !parts.isEmpty()) {
                    Map part = (Map) parts.get(0);

                    String text = (String) part.get("text");

                    return text; // ✅ THIS IS YOUR CLEAN JSON
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return response; // fallback
    }
}
