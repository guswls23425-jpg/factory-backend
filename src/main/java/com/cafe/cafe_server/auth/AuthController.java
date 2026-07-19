package com.cafe.cafe_server.auth;

import com.cafe.cafe_server.cafatable_x_y.Cafe;
import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import com.cafe.cafe_server.cafatable_x_y.Seat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class AuthController {

    private final OwnerRepository ownerRepository;
    private final CafeRepository cafeRepository;
    private final RestTemplate restTemplate;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    public AuthController(OwnerRepository ownerRepository, CafeRepository cafeRepository, RestTemplate restTemplate) {
        this.ownerRepository = ownerRepository;
        this.cafeRepository = cafeRepository;
        this.restTemplate = restTemplate;
    }

    public static class OwnerRegisterDto {
        public String userName;
        public String email;
        public String password;
        public String cafeName;
        public String address;
    }

    // 1️⃣ 회원가입 API (수정됨: Owner와 Cafe를 엮어서 저장!)
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody OwnerRegisterDto dto) {
        // 이메일 중복 검사
        if (ownerRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body("이미 존재하는 이메일입니다.");
        }
        
        // 사장님(Owner) 객체 세팅
        Owner owner = new Owner();
        owner.setUserName(dto.userName);
        owner.setEmail(dto.email);
        owner.setPassword(dto.password);

        // 카페(Cafe) 객체 세팅
        Cafe cafe = new Cafe();
        cafe.setName(dto.cafeName);
        if (dto.address != null && !dto.address.isBlank()) {
            cafe.setAddress(dto.address);
            geocodeAndSave(cafe, dto.address);
        }

        // ✨ 3. [근본 해결!] 16개의 기본 좌석(Seat)을 자동 생성해서 카페에 연결
        List<Seat> defaultSeats = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Seat seat = new Seat();
            seat.setName("테이블 " + (i + 1));
            seat.setStatus("available"); // 기본 상태는 '이용가능'
            
            // 프론트엔드의 GRID 간격에 맞춘 초기 x, y 좌표 계산
            int col = i % 4;
            int row = i / 4;
            // TABLE_WIDTH(120) + 20 = 140 / TABLE_HEIGHT(100) + 20 = 120
            seat.setPosX(col * 140 + 20); 
            seat.setPosY(row * 120 + 20); 

            // 좌석이 어느 카페 소속인지 명시!
            seat.setCafe(cafe);
            defaultSeats.add(seat);
        }
        
        // 카페에 16개의 좌석 리스트를 장착
        cafe.setSeats(defaultSeats);

        // ✨ 핵심: 서로 1:1 연결!
        cafe.setOwner(owner);
        owner.setCafe(cafe);

        // DB에 저장 (Cascade 설정 덕분에 Owner만 저장해도 Cafe까지 쏙 들어감!)
        ownerRepository.save(owner); 
        
        return ResponseEntity.ok("회원가입 성공");
    }

    // 2️⃣ 로그인 API (수정됨: 사장님을 통해 카페 이름을 꺼내옴!)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Owner loginReq) {
        Optional<Owner> ownerOpt = ownerRepository.findByEmail(loginReq.getEmail());
        
        if (ownerOpt.isPresent() && ownerOpt.get().getPassword().equals(loginReq.getPassword())) {
            Owner loggedInOwner = ownerOpt.get();
            // 👇 진짜 Cafe 객체에서 이름을 꺼내옴
            String realCafeName = loggedInOwner.getCafe().getName(); 

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", realCafeName + " 사장님 환영합니다!");
            responseBody.put("ownerId", loggedInOwner.getId());
            responseBody.put("cafeId", loggedInOwner.getCafe().getId()); // 프론트 localStorage용
            responseBody.put("cafeName", realCafeName);
            
            return ResponseEntity.ok(responseBody);
        }
        return ResponseEntity.status(401).body("이메일이나 비밀번호가 틀렸습니다.");
    }

    // 3️⃣ 특정 사장님(ID)의 카페 이름을 가져오는 API
    @GetMapping("/{id}/cafe-name")
    public ResponseEntity<String> getOwnerCafeName(@PathVariable("id") Long id) {
        return ownerRepository.findById(id)
                .map(owner -> ResponseEntity.ok(owner.getCafe().getName()))
                .orElse(ResponseEntity.notFound().build());
    }

    // 4️⃣ 전체 카페 위치 목록 (로그인 불필요, 지도 마커용)
    @GetMapping("/cafe-locations")
    public ResponseEntity<List<Map<String, Object>>> getCafeLocations() {
        List<Cafe> cafes = cafeRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Cafe cafe : cafes) {
            if (cafe.getLatitude() == null || cafe.getLongitude() == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cafeName", cafe.getName());
            item.put("address", cafe.getAddress());
            item.put("lat", cafe.getLatitude());
            item.put("lng", cafe.getLongitude());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 카카오 지오코딩: 주소 → 위도/경도
    @SuppressWarnings("unchecked")
    private void geocodeAndSave(Cafe cafe, String address) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = "https://dapi.kakao.com/v2/local/search/address.json?query="
                    + java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);

            ResponseEntity<Map> res = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (res.getBody() != null) {
                List<Map<String, Object>> documents = (List<Map<String, Object>>) res.getBody().get("documents");
                if (documents != null && !documents.isEmpty()) {
                    Map<String, Object> doc = documents.get(0);
                    cafe.setLatitude(Double.parseDouble(doc.get("y").toString()));
                    cafe.setLongitude(Double.parseDouble(doc.get("x").toString()));
                    log.info("지오코딩 성공: {} → ({}, {})", address, cafe.getLatitude(), cafe.getLongitude());
                }
            }
        } catch (Exception e) {
            log.warn("지오코딩 실패 (address={}): {}", address, e.getMessage());
        }
    }
}