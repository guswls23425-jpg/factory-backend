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
            m.put("createdAt", i.getCreatedAt().toString());
            return m;
        }).toList();
    }

    // ── Naver 뉴스/블로그 검색 ──────────────────────────────────────────
    private String fetchNaverTrends() {
        try {
            String[] queries = {"카페 트렌드", "카페 신메뉴", "카페 인테리어 트렌드"};
            StringBuilder sb = new StringBuilder();

            for (String q : queries) {
                String url = "https://openapi.naver.com/v1/search/news.json?query="
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
        try {
            String naverContext = fetchNaverTrends();

            String contextSection = naverContext.isBlank() ? "" :
                    "\n\n[최신 뉴스 참고 자료]\n" + naverContext + "\n위 최신 뉴스를 참고하여 답변하세요.\n";

            String prompt = """
                당신은 한국 카페 운영 컨설턴트입니다.%s
                위 최신 트렌드를 바탕으로 카페 "%s"에 바로 적용할 수 있는 창의적인 아이디어 4가지를 추천해주세요.

                각 아이디어는 반드시 아래 형식으로만 응답하세요 (JSON 배열):
                [
                  {
                    "emoji": "🧸",
                    "title": "아이디어 제목 (10자 이내)",
                    "description": "구체적인 적용 방법과 기대 효과 (2~3문장)"
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

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

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
                trendIdeaRepository.save(entity);
            }

            log.info("✅ [트렌드] {} 아이디어 {}개 저장 완료 (네이버 검색 {})", cafeName, ideas.size(),
                    naverContext.isBlank() ? "미사용" : "사용");

        } catch (Exception e) {
            log.error("❌ [트렌드] 생성 실패: {}", e.getMessage());
        }
    }
}
