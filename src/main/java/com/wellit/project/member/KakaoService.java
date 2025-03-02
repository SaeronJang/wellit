package com.wellit.project.member;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.codec.http.HttpHeaderValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class KakaoService {

    private String clientId;
    private final String KAUTH_TOKEN_URL_HOST;
    private final String KAUTH_USER_URL_HOST;
    private String redirectUri = "http://localhost:8080/callback";

    @Autowired
    public KakaoService(@Value("${kakao.client_id}") String clientId, MemberRepository memberRepository) {
        this.clientId = clientId;
        KAUTH_TOKEN_URL_HOST ="https://kauth.kakao.com";
        KAUTH_USER_URL_HOST = "https://kapi.kakao.com";
		this.memberRepository = memberRepository;
    }

    public String getAccessTokenFromKakao(String code) {
        String response = WebClient.create("https://kauth.kakao.com")
            .post()
            .uri(uriBuilder -> uriBuilder
                .path("/oauth/token")
                .build())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .bodyValue("grant_type=authorization_code" +
                       "&client_id=" + clientId +
                       "&redirect_uri=" + redirectUri +
                       "&code=" + code)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        log.info("Raw response: {}", response);

        try {
            KakaoTokenResponseDto responseDto = new ObjectMapper().readValue(response, KakaoTokenResponseDto.class);

            log.info(" [Kakao Service] Access Token ------> {}", responseDto.getAccessToken());
            log.info(" [Kakao Service] Refresh Token ------> {}", responseDto.getRefreshToken());
            log.info(" [Kakao Service] Id Token ------> {}", responseDto.getIdToken());
            log.info(" [Kakao Service] Scope ------> {}", responseDto.getScope());

            return responseDto.getAccessToken();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response", e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    
    
    private final MemberRepository memberRepository;


    
    public KakaoUserInfoResponseDto getUserInfo(String accessToken) {
    	
        String response = WebClient.create("https://kapi.kakao.com")
        		
            .get()
            .uri("/v2/user/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        log.info("Raw user info response: {}", response);

        try {
            KakaoUserInfoResponseDto userInfo = new ObjectMapper().readValue(response, KakaoUserInfoResponseDto.class);

            if (userInfo.getKakaoAccount() != null) {
                KakaoUserInfoResponseDto.KakaoAccount kakaoAccount = userInfo.getKakaoAccount();
                if (kakaoAccount.getProfile() != null) {
                    KakaoUserInfoResponseDto.KakaoAccount.Profile profile = kakaoAccount.getProfile();
                    log.info(" [Kakao Service] User Nickname ------> {}", profile.getNickName());
                    log.info(" [Kakao Service] User Profile Image ------> {}", profile.getProfileImageUrl());
                } else {
                    log.warn("KakaoAccount profile is null");
                }
            } else {
                log.warn("KakaoAccount is null");
            }

            return userInfo;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse user info response", e);
            throw new RuntimeException("Failed to parse user info response", e);
        }
    }



    public Member saveKakaoUser(KakaoUserInfoResponseDto kakaoUserInfo) {
        if (kakaoUserInfo == null || kakaoUserInfo.getKakaoAccount() == null) {
            throw new RuntimeException("Invalid Kakao user info received.");
        }

        KakaoUserInfoResponseDto.KakaoAccount kakaoAccount = kakaoUserInfo.getKakaoAccount();
        KakaoUserInfoResponseDto.KakaoAccount.Profile profile = kakaoAccount.getProfile();

        if (profile == null) {
            throw new RuntimeException("Profile information is missing in Kakao user info.");
        }

        Member member = new Member();

        // ID 설정
        member.setMemberId(String.valueOf(kakaoUserInfo.getId()));

        // 닉네임
        member.setMemberAlias(profile.getNickName());

        // 이미지 파일 URL
        member.setImageFile(profile.getProfileImageUrl());

        // 나머지 정보들 설정
        member.setMemberRegDate(LocalDateTime.now());
        member.setMileage(0); // 기본 마일리지 설정

        // 엔티티 저장 (Repository를 통해 저장)
        return memberRepository.save(member);
    }


    // 액세스 토큰을 받아 회원 정보를 저장하는 전체 로직
    public Member registerKakaoUser(String accessToken) {
    	System.out.println("21321321321");
        // 카카오 사용자 정보 가져오기
        KakaoUserInfoResponseDto kakaoUserInfo = getUserInfo(accessToken);
        // 가져온 사용자 정보를 DB에 저장
        return saveKakaoUser(kakaoUserInfo);
    }
}