package com.cafe.cafe_server.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    // 서버 시작 시 누락된 시간대 날씨 보완 (최대 24시간 이내)
    @EventListener(ApplicationReadyEvent.class)
    public void fillMissingWeatherOnStartup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today   = now.toLocalDate();
        int currentHour   = now.getHour();

        // 오늘 0시 ~ 현재 시각까지 누락된 시간대 수집
        for (int h = 0; h <= currentHour; h++) {
            if (weatherLogRepository.findByLogDateAndLogHour(today, h).isEmpty()) {
                log.info("[Weather] 서버 재시작 — 오늘 {}시 누락 데이터 수집 시도", h);
                fetchAndSaveWeatherForHour(today, h);
            }
        }

        // 어제 누락분도 보완 (서버가 자정 전후로 꺼졌을 경우)
        LocalDate yesterday = today.minusDays(1);
        for (int h = 0; h < 24; h++) {
            if (weatherLogRepository.findByLogDateAndLogHour(yesterday, h).isEmpty()) {
                log.info("[Weather] 서버 재시작 — 어제 {}시 누락 데이터 수집 시도", h);
                fetchAndSaveWeatherForHour(yesterday, h);
            }
        }
    }

    // 특정 날짜+시간으로 저장 (누락 보완용 — 현재 날씨를 해당 시간 슬롯에 기록)
    private void fetchAndSaveWeatherForHour(LocalDate date, int hour) {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?q=%s,%s&appid=%s&units=metric&lang=kr",
                    city, country, apiKey);
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return;

            WeatherLog log_ = buildWeatherLog(response, date, hour);
            weatherLogRepository.save(log_);
            log.info("[Weather] 누락 보완 저장 — {}일 {}시", date, hour);
        } catch (Exception e) {
            log.warn("[Weather] 누락 보완 실패 ({}일 {}시): {}", date, hour, e.getMessage());
        }
    }

    // 매시간 정각 실행 — 시간별 날씨 누적
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void fetchAndSaveWeather() {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?q=%s,%s&appid=%s&units=metric&lang=kr",
                    city, country, apiKey);

            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) { log.warn("[Weather] API 응답 없음"); return; }

            LocalDate today = LocalDate.now();
            int hour = LocalDateTime.now().getHour();

            if (weatherLogRepository.findByLogDateAndLogHour(today, hour).isPresent()) {
                log.debug("[Weather] {}시 데이터 이미 존재 — 스킵", hour);
                return;
            }

            WeatherLog log_ = buildWeatherLog(response, today, hour);
            weatherLogRepository.save(log_);
            log.info("[Weather] {}시 날씨 저장 완료 — {}°C, {}", hour, log_.getTemp(), log_.getDescription());

        } catch (Exception e) {
            log.error("[Weather] 날씨 데이터 수집 실패: {}", e.getMessage());
        }
    }

    private WeatherLog buildWeatherLog(Map<?, ?> response, LocalDate date, int hour) {
        WeatherLog log_ = new WeatherLog();
        log_.setLogDate(date);
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
        if (wind != null) log_.setWindSpeed(toDouble(wind.get("speed")));

        List<?> weatherList = (List<?>) response.get("weather");
        if (weatherList != null && !weatherList.isEmpty()) {
            Map<?, ?> weather = (Map<?, ?>) weatherList.get(0);
            log_.setDescription((String) weather.get("description"));
            log_.setIcon((String) weather.get("icon"));
            log_.setWeatherMain((String) weather.get("main"));
        }
        return log_;
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
