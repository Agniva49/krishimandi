package com.krishimandi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishimandi.model.Prediction;
import com.krishimandi.model.User;
import com.krishimandi.repository.PredictionRepository;
import com.krishimandi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final WeatherService weatherService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ml.service.url}")
    private String mlServiceUrl;

    public PredictionService(PredictionRepository predictionRepository,
                             UserRepository userRepository,
                             WeatherService weatherService,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.weatherService = weatherService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private static final Map<String, CropMeta> CROP_META;
    static {
        CROP_META = new HashMap<>();
        CROP_META.put("Rice",      new CropMeta("Rice",      2100, "Kharif", "₹/quintal"));
        CROP_META.put("Wheat",     new CropMeta("Wheat",     2400, "Rabi",   "₹/quintal"));
        CROP_META.put("Potato",    new CropMeta("Potato",    1200, "Rabi",   "₹/quintal"));
        CROP_META.put("Onion",     new CropMeta("Onion",     2800, "Kharif", "₹/quintal"));
        CROP_META.put("Tomato",    new CropMeta("Tomato",    3500, "All",    "₹/quintal"));
        CROP_META.put("Maize",     new CropMeta("Maize",     1900, "Kharif", "₹/quintal"));
        CROP_META.put("Soybean",   new CropMeta("Soybean",   4200, "Kharif", "₹/quintal"));
        CROP_META.put("Cotton",    new CropMeta("Cotton",    6500, "Kharif", "₹/quintal"));
        CROP_META.put("Sugarcane", new CropMeta("Sugarcane", 3200, "All",    "₹/quintal"));
        CROP_META.put("Mustard",   new CropMeta("Mustard",   5100, "Rabi",   "₹/quintal"));
        CROP_META.put("Groundnut", new CropMeta("Groundnut", 5800, "Kharif", "₹/quintal"));
        CROP_META.put("Chickpea",  new CropMeta("Chickpea",  5400, "Rabi",   "₹/quintal"));
        CROP_META.put("Lentil",    new CropMeta("Lentil",    7200, "Rabi",   "₹/quintal"));
        CROP_META.put("Bajra",     new CropMeta("Bajra",     2200, "Kharif", "₹/quintal"));
        CROP_META.put("Jowar",     new CropMeta("Jowar",     2900, "Kharif", "₹/quintal"));
    }

    // ─── Main Prediction Flow ────────────────────────
    public Map<String, Object> predict(String email, String cropName, String state,
                                       String district, int rangeDays, String modelPref) {

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Prediction> cached = predictionRepository.findRecentPrediction(
            cropName, state, rangeDays, LocalDateTime.now().minusHours(1));
        if (cached.isPresent()) {
            log.info("Returning cached prediction for {} in {}", cropName, state);
            return buildResponse(cached.get(), true);
        }

        Prediction.WeatherSnapshot weather = weatherService.getWeatherForState(state);

        CropMeta meta = CROP_META.getOrDefault(cropName,
            new CropMeta(cropName, 2000, "All", "₹/quintal"));

        Map<String, Object> mlPayload = buildMlPayload(
            cropName, state, district, rangeDays, modelPref, weather, meta);

        Map<String, Object> mlResult = callMlService(mlPayload);

        Prediction prediction = buildPrediction(user.getId(), cropName, state,
            district, rangeDays, weather, mlResult);
        predictionRepository.save(prediction);

        user.setTotalPredictions(user.getTotalPredictions() + 1);
        userRepository.save(user);

        log.info("Prediction saved — id: {}, price: ₹{}",
            prediction.getId(), prediction.getPredictedPrice());
        return buildResponse(prediction, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMlService(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                mlServiceUrl + "/predict", HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("ML service returned: " + response.getStatusCode());
        } catch (Exception e) {
            log.warn("ML microservice unavailable ({}), using fallback", e.getMessage());
            return fallbackPrediction(payload);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackPrediction(Map<String, Object> payload) {
        int basePrice = ((Number) payload.getOrDefault("currentPrice", 2000)).intValue();
        int rangeDays = ((Number) payload.getOrDefault("rangeDays", 14)).intValue();
        double trend  = ((Number) payload.getOrDefault("marketTrend", 1.0)).doubleValue();

        double predicted  = basePrice * (1 + trend / 100.0 * (rangeDays / 7.0));
        double confidence = 72.0 + new Random().nextDouble() * 10;

        List<Map<String, Object>> dailyForecast = new ArrayList<>();
        for (int i = 1; i <= rangeDays; i++) {
            double dayPrice = basePrice + (predicted - basePrice)
                * ((double) i / rangeDays) + (Math.random() - 0.5) * basePrice * 0.02;
            Map<String, Object> day = new HashMap<>();
            day.put("day",   i);
            day.put("price", Math.round(dayPrice));
            day.put("lower", Math.round(dayPrice * 0.97));
            day.put("upper", Math.round(dayPrice * 1.03));
            dailyForecast.add(day);
        }

        Map<String, Double> factors = new HashMap<>();
        factors.put("weatherImpact",   6.5);
        factors.put("supplyLevel",      5.0);
        factors.put("demandLevel",      6.0);
        factors.put("marketSentiment",  5.5);
        factors.put("seasonality",      7.0);
        factors.put("policySupport",    6.0);

        Map<String, Object> result = new HashMap<>();
        result.put("predictedPrice",    Math.round(predicted));
        result.put("priceLow",          Math.round(predicted * 0.95));
        result.put("priceHigh",         Math.round(predicted * 1.05));
        result.put("confidenceScore",   Math.round(confidence * 10.0) / 10.0);
        result.put("trend",             trend > 0 ? "rising" : trend < 0 ? "falling" : "stable");
        result.put("trendPercent",      Math.abs(trend));
        result.put("recommendation",    "Hold for " + (rangeDays / 2) + " more days.");
        result.put("bestSellingWindow", "Next 2–3 weeks");
        result.put("priceAlert",        predicted > basePrice * 1.1);
        result.put("alertMessage",      predicted > basePrice * 1.1
            ? "Price expected to rise >10% — ideal selling window approaching." : null);
        result.put("insight",           "Seasonal patterns suggest gradual price movement.");
        result.put("modelUsed",         "fallback-linear");
        result.put("dailyForecast",     dailyForecast);
        result.put("factorScores",      factors);
        return result;
    }

    private Map<String, Object> buildMlPayload(String cropName, String state, String district,
                                                int rangeDays, String modelPref,
                                                Prediction.WeatherSnapshot weather, CropMeta meta) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cropName",        cropName);
        payload.put("state",           state);
        payload.put("district",        district);
        payload.put("rangeDays",       rangeDays);
        payload.put("modelPreference", modelPref);
        payload.put("currentPrice",    meta.basePrice);
        payload.put("season",          meta.season);
        payload.put("currentMonth",    LocalDateTime.now().getMonthValue());
        payload.put("marketTrend",     1.0);
        if (weather != null) {
            payload.put("temperature", weather.getTemperature());
            payload.put("rainfall",    weather.getRainfall());
            payload.put("humidity",    weather.getHumidity());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Prediction buildPrediction(String userId, String cropName, String state,
                                        String district, int rangeDays,
                                        Prediction.WeatherSnapshot weather,
                                        Map<String, Object> ml) {

        List<Map<String, Object>> rawForecast =
            (List<Map<String, Object>>) ml.getOrDefault("dailyForecast", List.of());

        List<Prediction.DailyForecast> forecast = rawForecast.stream()
            .map(f -> new Prediction.DailyForecast(
                ((Number) f.get("day")).intValue(),
                ((Number) f.get("price")).doubleValue(),
                ((Number) f.getOrDefault("lower", f.get("price"))).doubleValue(),
                ((Number) f.getOrDefault("upper", f.get("price"))).doubleValue()
            )).collect(Collectors.toList());

        Map<String, Double> factors = new HashMap<>();
        Object fs = ml.get("factorScores");
        if (fs instanceof Map<?, ?> fsMap) {
            fsMap.forEach((k, v) -> factors.put(k.toString(), ((Number) v).doubleValue()));
        }

        return Prediction.builder()
            .userId(userId)
            .cropName(cropName)
            .state(state)
            .district(district)
            .predictionRangeDays(rangeDays)
            .predictedPrice(((Number) ml.getOrDefault("predictedPrice", 0)).doubleValue())
            .priceLow(((Number) ml.getOrDefault("priceLow", 0)).doubleValue())
            .priceHigh(((Number) ml.getOrDefault("priceHigh", 0)).doubleValue())
            .confidenceScore(((Number) ml.getOrDefault("confidenceScore", 0)).doubleValue())
            .trend((String) ml.getOrDefault("trend", "stable"))
            .trendPercent(((Number) ml.getOrDefault("trendPercent", 0)).doubleValue())
            .recommendation((String) ml.getOrDefault("recommendation", ""))
            .bestSellingWindow((String) ml.getOrDefault("bestSellingWindow", ""))
            .priceAlert(Boolean.TRUE.equals(ml.get("priceAlert")))
            .alertMessage((String) ml.get("alertMessage"))
            .insight((String) ml.getOrDefault("insight", ""))
            .modelUsed((String) ml.getOrDefault("modelUsed", "ensemble"))
            .factorScores(factors)
            .dailyForecast(forecast)
            .weather(weather)
            .build();
    }

    private Map<String, Object> buildResponse(Prediction p, boolean fromCache) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("predictionId",        p.getId());
        r.put("cropName",            p.getCropName());
        r.put("state",               p.getState());
        r.put("predictionRangeDays", p.getPredictionRangeDays());
        r.put("predictedPrice",      p.getPredictedPrice());
        Map<String, Object> range = new HashMap<>();
        range.put("low",  p.getPriceLow());
        range.put("high", p.getPriceHigh());
        r.put("priceRange",          range);
        r.put("confidenceScore",     p.getConfidenceScore());
        r.put("trend",               p.getTrend());
        r.put("trendPercent",        p.getTrendPercent());
        r.put("recommendation",      p.getRecommendation());
        r.put("bestSellingWindow",   p.getBestSellingWindow());
        r.put("priceAlert",          p.isPriceAlert());
        r.put("alertMessage",        p.getAlertMessage());
        r.put("insight",             p.getInsight());
        r.put("modelUsed",           p.getModelUsed());
        r.put("factorScores",        p.getFactorScores());
        r.put("dailyForecast",       p.getDailyForecast());
        r.put("weather",             p.getWeather());
        r.put("generatedAt",         p.getCreatedAt());
        r.put("cached",              fromCache);
        return r;
    }

    // ─── Other Methods ───────────────────────────────
    public Page<Prediction> getHistory(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return predictionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
    }

    public Optional<Prediction> getPredictionById(String id, String email) {
        return predictionRepository.findById(id).filter(p -> {
            User user = userRepository.findByEmail(email).orElse(null);
            return user != null && p.getUserId().equals(user.getId());
        });
    }

    public void deletePrediction(String id, String email) {
        getPredictionById(id, email).ifPresent(predictionRepository::delete);
    }

    public List<Map<String, Object>> getSupportedCrops() {
        return CROP_META.values().stream().map(m -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name",      m.name);
            entry.put("basePrice", m.basePrice);
            entry.put("season",    m.season);
            entry.put("unit",      m.unit);
            return entry;
        }).collect(Collectors.toList());
    }

    @Cacheable("marketSnapshot")
    public Map<String, Object> getMarketSnapshot(String crop, String state) {
        Map<String, Object> result = new HashMap<>();
        CROP_META.entrySet().stream()
            .filter(e -> crop == null || e.getKey().equalsIgnoreCase(crop))
            .forEach(e -> {
                Map<String, Object> info = new HashMap<>();
                info.put("price",  e.getValue().basePrice);
                info.put("unit",   e.getValue().unit);
                info.put("season", e.getValue().season);
                result.put(e.getKey(), info);
            });
        return result;
    }

    public Map<String, Object> getUserAnalytics(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        long total = predictionRepository.countByUserId(user.getId());
        List<Prediction> withActuals = predictionRepository.findWithActualPrices(user.getId());

        double accuracy = withActuals.isEmpty() ? 0 : withActuals.stream()
            .mapToDouble(p -> 100 - Math.abs(p.getPredictedPrice() - p.getActualPrice())
                / p.getActualPrice() * 100)
            .average().orElse(0);

        Map<String, Object> result = new HashMap<>();
        result.put("totalPredictions", total);
        result.put("modelAccuracy",    Math.round(accuracy * 10.0) / 10.0);
        result.put("memberSince",      user.getCreatedAt());
        return result;
    }

    public void recordActualPrice(String id, String email, double actual) {
        getPredictionById(id, email).ifPresent(p -> {
            p.setActualPrice(actual);
            predictionRepository.save(p);
        });
    }

    static class CropMeta {
        final String name, season, unit;
        final int basePrice;
        CropMeta(String name, int basePrice, String season, String unit) {
            this.name = name; this.basePrice = basePrice;
            this.season = season; this.unit = unit;
        }
    }
}
