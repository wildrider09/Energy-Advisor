package com.amazon.energyadvisor.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${weather.default-lat:12.9716}")
    private String lat;

    @Value("${weather.default-lon:77.5946}")
    private String lon;

    private double cachedTempF = Double.NaN;
    private String cachedSummary = "";
    private String cachedForecast = "";
    private String cachedTrend = "STABLE";
    private long cachedAt = 0;

    public double getOutdoorTemp() {
        refreshIfStale();
        return Double.isNaN(cachedTempF) ? 85.0 : cachedTempF;
    }

    public String getWeatherSummary() {
        refreshIfStale();
        return cachedSummary.isEmpty() ? String.format("%.0f°F outside", getOutdoorTemp()) : cachedSummary;
    }

    /** Get next 6 hours forecast summary for Bedrock context */
    public String getForecast() {
        refreshIfStale();
        return cachedForecast;
    }

    /** RISING, FALLING, or STABLE */
    public String getTrend() {
        refreshIfStale();
        return cachedTrend;
    }

    private void refreshIfStale() {
        if (System.currentTimeMillis() - cachedAt < 600_000) return;
        try {
            String url = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature"
                    + "&hourly=temperature_2m,weather_code&forecast_hours=6"
                    + "&temperature_unit=fahrenheit&wind_speed_unit=mph", lat, lon);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(resp.body());

            // Current
            JsonNode current = root.path("current");
            cachedTempF = current.path("temperature_2m").asDouble(85.0);
            double humidity = current.path("relative_humidity_2m").asDouble();
            double wind = current.path("wind_speed_10m").asDouble();
            double feelsLike = current.path("apparent_temperature").asDouble(cachedTempF);
            int code = current.path("weather_code").asInt();
            cachedSummary = String.format("%.0f°F (feels like %.0f°F), %s, humidity %.0f%%, wind %.0f mph (Bangalore)",
                    cachedTempF, feelsLike, weatherDescription(code), humidity, wind);

            // Hourly forecast
            JsonNode hourlyTemps = root.path("hourly").path("temperature_2m");
            JsonNode hourlyCodes = root.path("hourly").path("weather_code");
            List<String> hours = new ArrayList<>();
            double maxTemp = cachedTempF, minTemp = cachedTempF;
            for (int i = 0; i < Math.min(6, hourlyTemps.size()); i++) {
                double t = hourlyTemps.get(i).asDouble();
                maxTemp = Math.max(maxTemp, t);
                minTemp = Math.min(minTemp, t);
                hours.add(String.format("+%dh: %.0f°F %s", i + 1, t, weatherDescription(hourlyCodes.get(i).asInt())));
            }
            cachedForecast = String.format("Next 6h: high %.0f°F, low %.0f°F. %s", maxTemp, minTemp, String.join(", ", hours));

            // Trend
            if (hourlyTemps.size() >= 3) {
                double later = hourlyTemps.get(2).asDouble();
                if (later > cachedTempF + 3) cachedTrend = "RISING";
                else if (later < cachedTempF - 3) cachedTrend = "FALLING";
                else cachedTrend = "STABLE";
            }

            cachedAt = System.currentTimeMillis();
            log.info("Weather updated: {} trend={}", cachedSummary, cachedTrend);
        } catch (Exception e) {
            log.warn("Open-Meteo API failed: {}", e.getMessage());
        }
    }

    private String weatherDescription(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Partly cloudy";
        if (code <= 48) return "Foggy";
        if (code <= 67) return "Rainy";
        if (code <= 77) return "Snowy";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }
}
