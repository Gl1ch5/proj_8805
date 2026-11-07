package ru.gl1ch5.weather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Методы вызываются из JS: Native.requestForecast, Native.searchCity, Native.locate. */
public final class WeatherBridge {

    private static final String FORECAST_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final String GEO_BASE =
            "https://geocoding-api.open-meteo.com/v1/search";

    private final MainActivity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    WeatherBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void requestForecast(double lat, double lon, String name, String country) {
        String url = FORECAST_BASE
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                + "is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m"
                + "&hourly=temperature_2m,weather_code,precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "sunrise,sunset,precipitation_probability_max"
                + "&timezone=auto&forecast_days=7&wind_speed_unit=kmh";
        executor.execute(() -> {
            try {
                String raw = get(url);
                JSONObject place = new JSONObject()
                        .put("name", name == null ? "" : name)
                        .put("country", country == null ? "" : country)
                        .put("lat", lat)
                        .put("lon", lon);
                activity.deliver(new JSONObject()
                        .put("type", "forecast")
                        .put("place", place)
                        .put("data", new JSONObject(raw))
                        .toString());
            } catch (Exception e) {
                activity.deliverError("Не удалось получить прогноз: " + safeMessage(e));
            }
        });
    }

    @JavascriptInterface
    public void searchCity(String query) {
        if (query == null || query.trim().length() < 2) {
            activity.deliver("{\"type\":\"cities\",\"data\":{\"results\":[]}}");
            return;
        }
        final String encoded;
        try {
            encoded = URLEncoder.encode(query.trim(), "UTF-8");
        } catch (Exception e) {
            activity.deliverError("Некорректный запрос");
            return;
        }
        String url = GEO_BASE + "?name=" + encoded
                + "&count=8&language=ru&format=json";
        executor.execute(() -> {
            try {
                String raw = get(url);
                activity.deliver(new JSONObject()
                        .put("type", "cities")
                        .put("data", new JSONObject(raw))
                        .toString());
            } catch (Exception e) {
                activity.deliverError("Поиск города недоступен: " + safeMessage(e));
            }
        });
    }

    @JavascriptInterface
    public void locate() {
        if (!activity.hasLocationPermission()) {
            activity.deliver("{\"type\":\"need-permission\"}");
            activity.requestLocationPermission();
            return;
        }
        executor.execute(() -> {
            try {
                Location location = lastKnownLocation();
                if (location == null) {
                    activity.deliverError("Местоположение не определено. Включите GPS или найдите город.");
                    return;
                }
                activity.deliver(new JSONObject()
                        .put("type", "location")
                        .put("lat", location.getLatitude())
                        .put("lon", location.getLongitude())
                        .put("name", "Моё местоположение")
                        .toString());
            } catch (Exception e) {
                activity.deliverError("Местоположение недоступно");
            }
        });
    }

    @SuppressLint("MissingPermission")
    private Location lastKnownLocation() {
        LocationManager manager =
                (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        String provider = manager.getBestProvider(criteria, true);
        if (provider == null) {
            return null;
        }
        return manager.getLastKnownLocation(provider);
    }

    private static String get(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "WeatherWebView/1.0 (Android)");
            connection.setInstanceFollowRedirects(true);

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
