package com.cafe.cafe_server.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherLogRepository weatherLogRepository;
    private final RestTemplate restTemplate;

    @Value("${weather.api-key}")
    private String apiKey;

    @Value("${weather.city}")
    private String city;

    @Value("${weather.country}")
    private String country;

    // 매시간 정각 실행 — 시간별 날씨 누적
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void fetchAndSaveWeather() {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?q=%s,%s&appid=%s&units=metric&lang=kr",
                    city, country, apiKey);

            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                log.warn("[Weather] API 응답 없음");
                return;
            }

            LocalDate today = LocalDate.now();
            int hour = LocalDateTime.now().getHour();

            // 같은 날짜+시간 중복 방지
            if (weatherLogRepository.findByLogDateAndLogHour(today, hour).isPresent()) {
                log.debug("[Weather] {}시 데이터 이미 존재 — 스킵", hour);
                return;
            }

            WeatherLog log_ = new WeatherLog();
            log_.setLogDate(today);
            log_.setLogHour(hour);

            Map<?, ?> main = (Map<?, ?>) response.get("main");
            if (main != null) {
                log_.setTemp(toDouble(main.get("temp")));
                log_.setFeelsLike(toDouble(main.get("feels_like")));
                log_.setTempMin(toDouble(main.get("temp_min")));
                log_.setTempMax(toDouble(main.get("temp_max")));
                log_.setHumidity(toInt(main.get("humidity")));
            }

            Map<?, ?> wind = (Map<?, ?>) response.get("wind");
            if (wind != null) {
                log_.setWindSpeed(toDouble(wind.get("speed")));
            }

            List<?> weatherList = (List<?>) response.get("weather");
            if (weatherList != null && !weatherList.isEmpty()) {
                Map<?, ?> weather = (Map<?, ?>) weatherList.get(0);
                log_.setDescription((String) weather.get("description"));
                log_.setIcon((String) weather.get("icon"));
                log_.setWeatherMain((String) weather.get("main"));
            }

            weatherLogRepository.save(log_);
            log.info("[Weather] {}시 날씨 저장 완료 — {}°C, {}", hour, log_.getTemp(), log_.getDescription());

        } catch (Exception e) {
            log.error("[Weather] 날씨 데이터 수집 실패: {}", e.getMessage());
        }
    }

    // 오늘 날씨 (가장 최근 시간 기록)
    public Optional<WeatherLog> getTodayLatest() {
        List<WeatherLog> list = weatherLogRepository.findByLogDateOrderByLogHourAsc(LocalDate.now());
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(list.size() - 1));
    }

    // 날짜 범위 조회
    public List<WeatherLog> getByDateRange(LocalDate from, LocalDate to) {
        return weatherLogRepository.findByLogDateBetweenOrderByLogDateAscLogHourAsc(from, to);
    }

    // 특정 날짜 대표 날씨 (정오 우선, 없으면 첫 번째)
    public Optional<WeatherLog> getDailyRepresentative(LocalDate date) {
        Optional<WeatherLog> noon = weatherLogRepository.findByLogDateAndLogHour(date, 12);
        if (noon.isPresent()) return noon;
        return weatherLogRepository.findTopByLogDateOrderByLogHourAsc(date);
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        return ((Number) val).doubleValue();
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        return ((Number) val).intValue();
    }
}
