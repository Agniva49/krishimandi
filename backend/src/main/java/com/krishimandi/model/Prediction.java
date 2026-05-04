package com.krishimandi.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "predictions")
@CompoundIndex(def = "{'userId': 1, 'createdAt': -1}")
public class Prediction {

    @Id
    private String id;
    private String userId;
    private String cropName;
    private String state;
    private String district;
    private int predictionRangeDays;
    private double predictedPrice;
    private double priceLow;
    private double priceHigh;
    private double confidenceScore;
    private String trend;
    private double trendPercent;
    private String recommendation;
    private String bestSellingWindow;
    private boolean priceAlert;
    private String alertMessage;
    private String modelUsed;
    private Map<String, Double> modelScores;
    private Map<String, Double> factorScores;
    private List<DailyForecast> dailyForecast;
    private String insight;
    private WeatherSnapshot weather;
    private LocalDateTime createdAt = LocalDateTime.now();
    private Double actualPrice;
    private boolean cached = false;

    public Prediction() {}

    // Getters
    public String getId()                         { return id; }
    public String getUserId()                     { return userId; }
    public String getCropName()                   { return cropName; }
    public String getState()                      { return state; }
    public String getDistrict()                   { return district; }
    public int getPredictionRangeDays()           { return predictionRangeDays; }
    public double getPredictedPrice()             { return predictedPrice; }
    public double getPriceLow()                   { return priceLow; }
    public double getPriceHigh()                  { return priceHigh; }
    public double getConfidenceScore()            { return confidenceScore; }
    public String getTrend()                      { return trend; }
    public double getTrendPercent()               { return trendPercent; }
    public String getRecommendation()             { return recommendation; }
    public String getBestSellingWindow()          { return bestSellingWindow; }
    public boolean isPriceAlert()                 { return priceAlert; }
    public String getAlertMessage()               { return alertMessage; }
    public String getModelUsed()                  { return modelUsed; }
    public Map<String, Double> getModelScores()   { return modelScores; }
    public Map<String, Double> getFactorScores()  { return factorScores; }
    public List<DailyForecast> getDailyForecast() { return dailyForecast; }
    public String getInsight()                    { return insight; }
    public WeatherSnapshot getWeather()           { return weather; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public Double getActualPrice()                { return actualPrice; }
    public boolean isCached()                     { return cached; }

    // Setters
    public void setId(String id)                          { this.id = id; }
    public void setUserId(String userId)                  { this.userId = userId; }
    public void setCropName(String cropName)              { this.cropName = cropName; }
    public void setState(String state)                    { this.state = state; }
    public void setDistrict(String district)              { this.district = district; }
    public void setPredictionRangeDays(int n)             { this.predictionRangeDays = n; }
    public void setPredictedPrice(double v)               { this.predictedPrice = v; }
    public void setPriceLow(double v)                     { this.priceLow = v; }
    public void setPriceHigh(double v)                    { this.priceHigh = v; }
    public void setConfidenceScore(double v)              { this.confidenceScore = v; }
    public void setTrend(String trend)                    { this.trend = trend; }
    public void setTrendPercent(double v)                 { this.trendPercent = v; }
    public void setRecommendation(String v)               { this.recommendation = v; }
    public void setBestSellingWindow(String v)            { this.bestSellingWindow = v; }
    public void setPriceAlert(boolean v)                  { this.priceAlert = v; }
    public void setAlertMessage(String v)                 { this.alertMessage = v; }
    public void setModelUsed(String v)                    { this.modelUsed = v; }
    public void setModelScores(Map<String, Double> v)     { this.modelScores = v; }
    public void setFactorScores(Map<String, Double> v)    { this.factorScores = v; }
    public void setDailyForecast(List<DailyForecast> v)   { this.dailyForecast = v; }
    public void setInsight(String v)                      { this.insight = v; }
    public void setWeather(WeatherSnapshot v)             { this.weather = v; }
    public void setCreatedAt(LocalDateTime v)             { this.createdAt = v; }
    public void setActualPrice(Double v)                  { this.actualPrice = v; }
    public void setCached(boolean v)                      { this.cached = v; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Prediction p = new Prediction();
        public Builder userId(String v)                  { p.userId = v; return this; }
        public Builder cropName(String v)                { p.cropName = v; return this; }
        public Builder state(String v)                   { p.state = v; return this; }
        public Builder district(String v)                { p.district = v; return this; }
        public Builder predictionRangeDays(int v)        { p.predictionRangeDays = v; return this; }
        public Builder predictedPrice(double v)          { p.predictedPrice = v; return this; }
        public Builder priceLow(double v)                { p.priceLow = v; return this; }
        public Builder priceHigh(double v)               { p.priceHigh = v; return this; }
        public Builder confidenceScore(double v)         { p.confidenceScore = v; return this; }
        public Builder trend(String v)                   { p.trend = v; return this; }
        public Builder trendPercent(double v)            { p.trendPercent = v; return this; }
        public Builder recommendation(String v)          { p.recommendation = v; return this; }
        public Builder bestSellingWindow(String v)       { p.bestSellingWindow = v; return this; }
        public Builder priceAlert(boolean v)             { p.priceAlert = v; return this; }
        public Builder alertMessage(String v)            { p.alertMessage = v; return this; }
        public Builder insight(String v)                 { p.insight = v; return this; }
        public Builder modelUsed(String v)               { p.modelUsed = v; return this; }
        public Builder factorScores(Map<String, Double> v){ p.factorScores = v; return this; }
        public Builder dailyForecast(List<DailyForecast> v){ p.dailyForecast = v; return this; }
        public Builder weather(WeatherSnapshot v)        { p.weather = v; return this; }
        public Builder cached(boolean v)                 { p.cached = v; return this; }
        public Prediction build()                        { return p; }
    }

    // ─── Nested: DailyForecast ────────────────────────
    public static class DailyForecast {
        private int day;
        private double price;
        private double lower;
        private double upper;

        public DailyForecast() {}
        public DailyForecast(int day, double price, double lower, double upper) {
            this.day = day; this.price = price;
            this.lower = lower; this.upper = upper;
        }
        public int getDay()       { return day; }
        public double getPrice()  { return price; }
        public double getLower()  { return lower; }
        public double getUpper()  { return upper; }
        public void setDay(int v)       { this.day = v; }
        public void setPrice(double v)  { this.price = v; }
        public void setLower(double v)  { this.lower = v; }
        public void setUpper(double v)  { this.upper = v; }
    }

    // ─── Nested: WeatherSnapshot ──────────────────────
    public static class WeatherSnapshot {
        private double temperature;
        private double rainfall;
        private double humidity;
        private String condition;
        private String city;

        public WeatherSnapshot() {}
        public WeatherSnapshot(double temperature, double rainfall,
                               double humidity, String condition, String city) {
            this.temperature = temperature; this.rainfall = rainfall;
            this.humidity = humidity; this.condition = condition; this.city = city;
        }
        public double getTemperature() { return temperature; }
        public double getRainfall()    { return rainfall; }
        public double getHumidity()    { return humidity; }
        public String getCondition()   { return condition; }
        public String getCity()        { return city; }
        public void setTemperature(double v) { this.temperature = v; }
        public void setRainfall(double v)    { this.rainfall = v; }
        public void setHumidity(double v)    { this.humidity = v; }
        public void setCondition(String v)   { this.condition = v; }
        public void setCity(String v)        { this.city = v; }
    }
}
