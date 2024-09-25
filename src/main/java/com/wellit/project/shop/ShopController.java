package com.wellit.project.shop;

import com.wellit.project.order.CartItemRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RequestMapping("/shop")
@Controller
@Log4j2
public class ShopController {

    private final ShopService shopService;
    private final ProductRepository productRepository;
    private final ProdReviewService reviewService;

    private static final String UPLOAD_DIR = "C:/uploads/";

    /*인기상품 리스트 이동*/
    @GetMapping("/")
    @ResponseBody
    public String getShopPopular() {
        return "shop_popular";
    }

    /*상품 리스트 페이지 이동*/
    @GetMapping("/list")
    public String getShopList(Model model) {

        List<Product> prodList = shopService.getProdCateList();
        List<ProdCnt> prodCnts = shopService.getProdCntList();

        // prodId를 키로, 리뷰, 찜 카운트 Map
        Map<Long, Integer> revCntMap = prodCnts.stream()
                                               .collect(Collectors.toMap(ProdCnt::getProdId, ProdCnt::getRevCnt));
        Map<Long, Integer> favoriteCntMap = prodCnts.stream()
                                                    .collect(Collectors.toMap(ProdCnt::getProdId,
                                                                              ProdCnt::getFavoriteCnt));

        model.addAttribute("prodlist", prodList);
        model.addAttribute("revCntMap", revCntMap);
        model.addAttribute("favoriteCntMap", favoriteCntMap);

        return "shop/shop_list";
    }


    /*상품 상세페이지 이동*/
    @GetMapping("/detail/{prodId}")
    public String getShopDetail(Model model, @PathVariable("prodId") Long prodId,
                                @AuthenticationPrincipal UserDetails userDetails) {
        String memberId = null;
        //멤버아이디 확인
        if (userDetails != null) {
            memberId = userDetails.getUsername();
            boolean favorite = shopService.isFavoriteProduct(prodId, memberId);
            model.addAttribute("memberId", memberId);
            model.addAttribute("favorite", favorite);
        } else {
            model.addAttribute("memberId", null);
            model.addAttribute("favorite", false);
        }


        Product product = shopService.getOneProd(prodId);
        if(product.getViewCnt() == null) {
            product.setViewCnt(1);
        }else {
            product.setViewCnt(product.getViewCnt() + 1);
        }
        productRepository.save(product);

        // 이미지 리스트를 prod_image_num 순서로 정렬
        List<ProdImage> sortedImages = product.getProdImages().stream()
                                              .sorted(Comparator.comparing(ProdImage::getProdImageNum)) // prod_image_num으로 정렬
                                              .collect(Collectors.toList());

        List<ProdReview> imgReviewList = shopService.getImgReviews(prodId);
        CartItemRequest cartItemRequest = new CartItemRequest();

        model.addAttribute("cartItemRequest", cartItemRequest);

        model.addAttribute("reviewCnt", shopService.getCountProdReview(prodId));
        model.addAttribute("sortedImages", sortedImages); // 정렬된 이미지 리스트
        model.addAttribute("product", product);
        model.addAttribute("imgReviewList", imgReviewList);

        return "shop/shop_detail";
    }

    //리뷰 페이징
    @GetMapping("/review/{prodId}/{revPage}")
    @ResponseBody
    public Map<String, Object> listProducts(
            @PathVariable("revPage") int revPage,
            @PathVariable("prodId") int prodId) {

        Page<ProdReviewDTO> pagedRevList = shopService.getPagedRevList(revPage, prodId);

        Map<String, Object> response = new HashMap<>();
        response.put("reviews", pagedRevList.getContent());
        response.put("totalPages", pagedRevList.getTotalPages());

        return response;
    }

    // admin:상품 리스트 이동
    @GetMapping("/admin/list")
    public String getAdminProductList(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return "redirect:/shop/list";  // 상품 리스트 페이지로 리다이렉트
        }



        return "/shop/admin_productList";
    }


    //admin:상품 삭제 페이지
    @DeleteMapping("/delete/{prodId}")
    public String deleteProduct(@AuthenticationPrincipal UserDetails userDetails,
                                @PathVariable(name="prodId") Long prodId) {
        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return "redirect:/shop/list";  // 상품 리스트 페이지로 리다이렉트
        }
        shopService.deleteProduct(prodId);

        return "redirect:/shop/list";
    }


    // admin:상품 생성 폼 열기
    @GetMapping("/admin/form")
    public String productForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return "redirect:/shop/list";  // 상품 리스트 페이지로 리다이렉트
        }

        model.addAttribute("productForm", new ProductForm());
        return "/shop/shop_form";
    }

    //admin:상품 저장하기
    @PostMapping("/save")
    public String saveProduct(@AuthenticationPrincipal UserDetails userDetails,
                              @ModelAttribute ProductForm productForm) throws IOException {
        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return "redirect:/shop/list";  // 상품 리스트 페이지로 리다이렉트
        }
        List<MultipartFile> imageFiles = productForm.getProdImages();
        MultipartFile thumbFile = productForm.getProdMainImg();

        shopService.saveProduct(productForm, thumbFile, imageFiles);

        return "redirect:/shop/list";
    }


    @GetMapping("/admin/edit/{prodId}")
    public String editProduct(@AuthenticationPrincipal UserDetails userDetails, @PathVariable("prodId") Long prodId,
                              Model model) {
        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return "redirect:/shop/list";  // 상품 리스트 페이지로 리다이렉트
        }

        Product product = shopService.getOneProd(prodId);  // 기존 상품 조회
        ProductForm productForm = new ProductForm(product);  // ProductForm에 매핑

        // 이미지 리스트를 순서대로 정렬
        List<ProdImage> imageFiles = product.getProdImages().stream()
                                            .sorted(Comparator.comparingInt(ProdImage::getProdImageNum))  // 이미지 순서대로 정렬
                                            .collect(Collectors.toList());

        List<String> toBeDeleted = new ArrayList<>();

        String prodMainImg = product.getProdMainImg();

        model.addAttribute("productForm", productForm);
        model.addAttribute("imageFiles", imageFiles);  // 순서대로 정렬된 이미지 리스트
        model.addAttribute("prodMainImg", prodMainImg);
        model.addAttribute("prodDiscount", (product.getProdDiscount() != null) ? product.getProdDiscount() : 0);
        model.addAttribute("toBeDeleted", toBeDeleted);

        return "/shop/shop_form";  // 수정 폼으로 이동
    }


    //admin:상품 수정내용 저장하기
    @PostMapping("/update/{prodId}")
    @ResponseBody
    public ResponseEntity<String> updateProduct(@AuthenticationPrincipal UserDetails userDetails,
                                                @PathVariable Long prodId,
                                                @ModelAttribute ProductForm productForm,
                                                @RequestParam(required=false) List<String> toBeDeleted,
                                                @RequestParam(value = "existingImages[]",required=false) List<String> existingImages,
                                                @RequestParam(value = "existingImageOrders[]",required=false) List<Integer> existingImageOrders,
                                                @RequestParam(value = "newImages[]",required=false) List<MultipartFile> newImages,
                                                @RequestParam(value = "newImageOrders[]",required=false) List<Integer> newImageOrders
                                               ) throws IOException {

        // 현재 로그인한 사용자가 admin인지 확인
        if (userDetails == null || !"admin".equals(userDetails.getUsername())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("{\"status\":\"fail\", \"message\":\"Unauthorized access\"}");
        }

        // 상품 업데이트 처리
        MultipartFile thumbFile = productForm.getProdMainImg();
        shopService.updateProduct(prodId, productForm, thumbFile, toBeDeleted, existingImages, existingImageOrders, newImages, newImageOrders);

        return ResponseEntity.ok("{\"status\":\"success\"}");
    }



    // 상품 상세페이지 : 찜하기 버튼
    @PostMapping("/favorite/change")
    @ResponseBody
    public ResponseEntity<String> isFavoriteProduct(@RequestParam(value="prodId",required=true) Long prodId,
                                                    @RequestParam(value="memberId",required=true) String memberId) {

        log.info(prodId);
        log.info(memberId);
        try {
            if (memberId == null) {
                throw new RuntimeException("로그인해주세요");
            }

            //현재 찜 리스트에 있는 지 확인
            boolean isFavorite = shopService.isFavoriteProduct(prodId, memberId);

            //없는 경우 새로 추가
            if (isFavorite == false) {
                shopService.addFavoriteProduct(prodId, memberId);
                return ResponseEntity.ok("찜 목록을 추가하였습니다.");
            } else {
                shopService.removeFavoriteProduct(prodId, memberId);
                return ResponseEntity.ok("찜 목록을 해제하였습니다.");
            }


        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    //개인별 찜 리스트 리턴
    @GetMapping("/favorite/list/{memberId}")
    @ResponseBody
    public ResponseEntity<List<FavoriteProductDTO>> getFavoriteProductList(
            @PathVariable(value="memberId") String memberId) {

        List<FavoriteProductDTO> favoriteList = shopService.getFavoriteProductList(memberId);

        return ResponseEntity.ok(favoriteList);
    }



    // admin페이지 상품 리스트 데이터 받아오기
    @GetMapping("/api/products")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String prodCate,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "page", defaultValue = "1") int page) {

        // 서비스 호출
        Page<ProductAdminDTO> productsPage = shopService.findProducts(search, prodCate, startDate, endDate, page);

        // 반환할 데이터 구성
        Map<String, Object> response = new HashMap<>();
        response.put("products", productsPage.getContent());
        response.put("totalPages", productsPage.getTotalPages());
        response.put("currentPage", productsPage.getNumber() + 1);
        response.put("totalItems", productsPage.getTotalElements());

        return ResponseEntity.ok(response);
    }



}






