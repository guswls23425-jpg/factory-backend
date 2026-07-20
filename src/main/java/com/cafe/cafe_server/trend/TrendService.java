package com.cafe.cafe_server.trend;

import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendService {

    private final TrendIdeaRepository trendIdeaRepository;
    private final CafeRepository cafeRepository;
    private final RestTemplate restTemplate;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

    // 카페별 중복 호출 방지
    private final Set<String> generating = ConcurrentHashMap.newKeySet();

    // 분당 14회 이하 제한 (무료 티어 15회/분)
    private final AtomicInteger minuteCount = new AtomicInteger(0);
    private volatile long minuteStart = System.currentTimeMillis();

    private boolean acquireRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        if (now - minuteStart >= 60_000) {
            minuteStart = now;
            minuteCount.set(0);
        }
        if (minuteCount.incrementAndGet() > 14) {
            long wait = 60_000 - (System.currentTimeMillis() - minuteStart) + 500;
            log.warn("⏳ [트렌드] 분당 한도 도달, {}ms 대기", wait);
            Thread.sleep(Math.max(wait, 0));
            minuteStart = System.currentTimeMillis();
            minuteCount.set(1);
        }
        return true;
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void scheduledUpdate() {
        log.info("📈 [트렌드] 주간 자동 업데이트 시작");
        cafeRepository.findAll().forEach(cafe -> generateAndSave(cafe.getName()));
    }

    @Transactional
    public List<Map<String, Object>> getOrGenerate(String cafeName) {
        List<TrendIdea> ideas = trendIdeaRepository.findByCafeNameOrderByCreatedAtDesc(cafeName);

        boolean stale = ideas.isEmpty() ||
                ideas.get(0).getCreatedAt().isBefore(LocalDateTime.now().minusDays(7));
        if (stale) {
            generateAndSave(cafeName);
            ideas = trendIdeaRepository.findByCafeNameOrderByCreatedAtDesc(cafeName);
        }

        return toMapList(ideas);
    }

    @Transactional
    public List<Map<String, Object>> refresh(String cafeName) {
        generateAndSave(cafeName);
        return toMapList(trendIdeaRepository.findByCafeNameOrderByCreatedAtDesc(cafeName));
    }

    private List<Map<String, Object>> toMapList(List<TrendIdea> ideas) {
        return ideas.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("emoji", i.getEmoji());
            m.put("title", i.getTitle());
            m.put("description", i.getDescription());
            m.put("category", i.getCategory() != null ? i.getCategory() : "idea");
            m.put("createdAt", i.getCreatedAt().toString());
            return m;
        }).toList();
    }

    // ── Naver 블로그 검색 ──────────────────────────────────────────────
    private String fetchNaverTrends() {
        try {
            String[] queries = {"요즘 카페 유행", "카페 인기 음료", "베이커리 유행 디저트", "카페 릴스 아이디어"};
            StringBuilder sb = new StringBuilder();

            for (String q : queries) {
                String url = "https://openapi.naver.com/v1/search/blog.json?query="
                        + java.net.URLEncoder.encode(q, "UTF-8") + "&display=5&sort=date";

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Naver-Client-Id", naverClientId);
                headers.set("X-Naver-Client-Secret", naverClientSecret);

                ResponseEntity<Map> res = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

                if (res.getBody() == null) continue;

                List<Map<String, Object>> items = (List<Map<String, Object>>) res.getBody().get("items");
                if (items == null) continue;

                for (Map<String, Object> item : items) {
                    String title = item.getOrDefault("title", "").toString()
                            .replaceAll("<[^>]+>", ""); // HTML 태그 제거
                    String desc = item.getOrDefault("description", "").toString()
                            .replaceAll("<[^>]+>", "");
                    sb.append("- ").append(title).append(": ").append(desc).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("⚠️ [트렌드] 네이버 검색 실패, 기본 프롬프트 사용: {}", e.getMessage());
            return "";
        }
    }

    // ── Gemini 호출 ─────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void generateAndSave(String cafeName) {
        // 이미 생성 중이면 스킵
        if (!generating.add(cafeName)) {
            log.info("⏭️ [트렌드] {} 이미 생성 중, 스킵", cafeName);
            return;
        }
        try {
            acquireRateLimit();
            String naverContext = fetchNaverTrends();

            String contextSection = naverContext.isBlank() ? "" :
                    "\n\n[최신 블로그/SNS 트렌드 참고 자료]\n" + naverContext + "\n위 자료를 참고하여 답변하세요.\n";

            String prompt = """
                당신은 한국 카페 운영 전문 컨설턴트입니다.%s

                카페 "%s"의 사장님이 유행에 뒤쳐지지 않도록 매주 트렌드 브리핑을 제공합니다.
                틱톡, 인스타그램 릴스, 유튜브 쇼츠에서 요즘 MZ세대 사이에 바이럴되고 있는 트렌드를 반영하여
                아래 두 가지를 모두 알려주세요.

                1. category "menu" — 이번 주 인기 메뉴 4가지:
                   지금 한국 카페들에서 실제로 유행 중인 음료/베이커리 메뉴.
                   (예: 특정 시그니처 음료, 유행 디저트, 신상 베이커리 스타일)
                   description에는 어떤 메뉴인지와 왜 지금 인기인지 설명.

                2. category "idea" — 적용 아이디어 4가지:
                   SNS에서 화제가 되거나 바이럴될 수 있고, 고객이 직접 찍고 공유하고 싶게 만들며,
                   당장 카페에서 실행 가능한 현실적인 아이디어.
                   description에는 구체적인 적용 방법과 SNS 바이럴 포인트 설명.

                반드시 아래 형식의 JSON 배열 하나로만 응답하세요 (총 8개):
                [
                  {
                    "category": "menu",
                    "emoji": "🍓",
                    "title": "메뉴/아이디어 이름 (10자 이내)",
                    "description": "설명 (2~3문장)"
                  }
                ]

                JSON 외 다른 텍스트는 절대 포함하지 마세요.
                """.formatted(contextSection, cafeName);

            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt))
                ))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + geminiApiKey;

            ResponseEntity<Map> res = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (res.getBody() == null) return;

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) res.getBody().get("candidates");
            if (candidates == null || candidates.isEmpty()) return;

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = parts.get(0).get("text").toString().trim();

            if (text.startsWith("```")) {
                text = text.replaceAll("```json", "").replaceAll("```", "").trim();
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, String>> ideas = mapper.readValue(text,
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            trendIdeaRepository.deleteByCafeName(cafeName);
            for (Map<String, String> idea : ideas) {
                TrendIdea entity = new TrendIdea();
                entity.setCafeName(cafeName);
                entity.setEmoji(idea.getOrDefault("emoji", "✨"));
                entity.setTitle(idea.getOrDefault("title", ""));
                entity.setDescription(idea.getOrDefault("description", ""));
                entity.setCategory("menu".equals(idea.get("category")) ? "menu" : "idea");
                trendIdeaRepository.save(entity);
            }

            log.info("✅ [트렌드] {} 아이디어 {}개 저장 완료 (네이버 검색 {})", cafeName, ideas.size(),
                    naverContext.isBlank() ? "미사용" : "사용");

        } catch (Exception e) {
            log.error("❌ [트렌드] 생성 실패: {}", e.getMessage());
        } finally {
            generating.remove(cafeName);
        }
    }
}
