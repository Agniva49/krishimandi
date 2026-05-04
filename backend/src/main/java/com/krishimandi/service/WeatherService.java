package com.krishimandi.service;

import com.krishimandi.model.Prediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestTemplate restTemplate;

    @Value("${app.weather.api.key}")
    private String apiKey;

    @Value("${app.weather.api.url}")
    private String apiUrl;

    private static final Map<String, String> STATE_CITIES = new HashMap<>();
    static {
        STATE_CITIES.put("Punjab",         "Amritsar");
        STATE_CITIES.put("Uttar Pradesh",  "Lucknow");
        STATE_CITIES.put("Maharashtra",    "Pune");
        STATE_CITIES.put("Madhya Pradesh", "Bhopal");
        STATE_CITIES.put("Rajasthan",      "Jaipur");
        STATE_CITIES.put("Karnataka",      "Bangalore");
        STATE_CITIES.put("Andhra Pradesh", "Vijayawada");
        STATE_CITIES.put("Bihar",          "Patna");
        STATE_CITIES.put("West Bengal",    "Kolkata");
        STATE_CITIES.put("Gujarat",        "Ahmedabad");
        STATE_CITIES.put("Haryana",        "Chandigarh");
        STATE_CITIES.put("Tamil Nadu",     "Chennai");
        STATE_CITIES.put("Telangana",      "Hyderabad");
        STATE_CITIES.put("Odisha",         "Bhubaneswar");
        STATE_CITIES.put("Kerala",         "Thiruvananthapuram");
    }

    private static final double[][] SEASONAL_DEFAULTS = {
        {15, 12, 55}, {18, 8, 50}, {25, 15, 45}, {32, 20, 40},
        {38, 30, 35}, {35,120, 70}, {30,200, 85}, {29,180, 88},
        {28,100, 80}, {24, 40, 65}, {18, 10, 60}, {13, 5, 58}
    };

    public WeatherService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "weather", key = "#state")
    public Prediction.WeatherSnapshot getWeatherForState(String state) {
        String city = STATE_CITIES.getOrDefault(state, "Delhi");
        try {
            String url = String.format("%s/weather?q=%s,IN&appid=%s&units=metric",
                apiUrl, city, apiKey);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> main = (Map<String, Object>) response.get("main");
                @SuppressWarnings("unchecked")
                Map<String, Object> rain = (Map<String, Object>) response.getOrDefault("rain", new HashMap<>());
                @SuppressWarnings("unchecked")
                List<?> weatherList = (List<?>) response.get("weather");
                @SuppressWarnings("unchecked")
                Map<String, Object> weatherDesc = (weatherList != null && !weatherList.isEmpty())
                    ? (Map<String, Object>) weatherList.get(0) : new HashMap<>();

                return new Prediction.WeatherSnapshot(
                    ((Number) main.getOrDefault("temp",     25)).doubleValue(),
                    ((Number) rain.getOrDefault("1h",        0)).doubleValue(),
                    ((Number) main.getOrDefault("humidity", 60)).doubleValue(),
                    (String) weatherDesc.getOrDefault("main", "Clear"),
                    city
                );
            }
        } catch (Exception e) {
            log.warn("Weather API failed for {}: {} — using seasonal fallback", city, e.getMessage());
        }
        return getSeasonalFallback(city);
    }

    private Prediction.WeatherSnapshot getSeasonalFallback(String city) {
        int month = LocalDate.now().getMonthValue() - 1;
        double[] d = SEASONAL_DEFAULTS[month];
        return new Prediction.WeatherSnapshot(d[0], d[1], d[2], "Seasonal Average", city);
    }
}
