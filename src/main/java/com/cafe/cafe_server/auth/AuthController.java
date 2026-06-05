// next.js에서 오는 로그인 데이터 받아서 처리
package com.cafe.cafe_server.auth;

import com.cafe.cafe_server.cafatable_x_y.Cafe; // 본인 패키지 경로에 맞게 임포트 확인!
import com.cafe.cafe_server.cafatable_x_y.Seat;
//import com.cafe.cafe_server.auth.Owner; // 본인 패키지 경로에 맞게 임포트 확인!
//import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class AuthController {

    private final OwnerRepository ownerRepository;

    //@Autowired
    public AuthController(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    // 💡 프론트엔드에서 넘어오는 데이터를 받기 위한 전용 그릇(DTO)
    public static class OwnerRegisterDto {
        public String userName;
        public String email;
        public String password;
        public String cafeName;
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
        cafe.setName(dto.cafeName); // Cafe.java에서 name으로 지었으니 getName()/setName() 사용

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

    // 3️⃣ 특정 사장님(ID)의 카페 이름을 가져오는 API (수정됨: Cafe 객체 경유)
    @GetMapping("/{id}/cafe-name")
    public ResponseEntity<String> getOwnerCafeName(@PathVariable("id") Long id) {
        return ownerRepository.findById(id)
                // ✅ 변경: Owner를 거쳐서 Cafe의 name을 가져옴
                .map(owner -> ResponseEntity.ok(owner.getCafe().getName())) 
                .orElse(ResponseEntity.notFound().build());
    }
}