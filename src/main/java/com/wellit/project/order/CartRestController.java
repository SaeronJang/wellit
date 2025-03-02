package com.wellit.project.order;

import com.wellit.project.member.Member;
import com.wellit.project.member.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Long.parseLong;

@RestController
@RequestMapping("/cart/data")
@RequiredArgsConstructor
@Log4j2
public class CartRestController {

    private final CartService cartService;
    private final MemberService memberService;



    /* 카트에 상품 추가 */
    @PostMapping("/add")
    public ResponseEntity<Map<String, String>> addToCart(@RequestBody CartItemRequest cartItemRequest, @AuthenticationPrincipal UserDetails userDetails){

        cartService.addToCart(cartItemRequest, memberService.getMember(userDetails.getUsername()));

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cart updated successfully");

        return ResponseEntity.ok(response);
    }

    @Transactional
    @PostMapping("/delete")
    public ResponseEntity<Map<String , String >> removeAtCart(@RequestBody String strProdId, @AuthenticationPrincipal UserDetails userDetails){
        Long prodId = Long.parseLong(strProdId.replace("{\"prodId\":","").replace("}",""));
        cartService.removeCartItem(prodId, memberService.getMember(userDetails.getUsername()).getCart());

        Map<String, String > response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cart updated successfully");

        return ResponseEntity.ok(response);
    }




    @GetMapping("/item-count")
    public ResponseEntity<Map<String, Object>> getCartItemCount(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        if (userDetails != null) {
            String username = userDetails.getUsername();
            int cartItemCount = cartService.getCartItemCountByUser(username);
            response.put("cartItemCount", cartItemCount);
        } else {
            response.put("cartItemCount", 0);  // 비로그인 상태일 때 0 반환
        }
        return ResponseEntity.ok(response);
    }


















}
