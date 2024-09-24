package com.wellit.project.order;

import com.wellit.project.shop.ProdReview;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemDTO {

    private Long prodId; //상품id
    private String prodName;
    private String prodThumb;
    private Integer prodOrgPrice; //상품 원래 가격
    private Integer prodFinalPrice; //판매 가격(할인율 반영)



    private Integer quantity;
    private Integer sumOrgPrice; //원래 상품 가격 * 수량
    private Integer sumDiscPrice; //해당 상품의 할인 금액(음수)
    private Integer sumFinalPrice; //구매가격 합계

    private boolean reviewed; //리뷰 작성 완료 여부
    private Long orderItemId; //OrderItem 엔티티 id
    private ProdReview prodReview;



}
