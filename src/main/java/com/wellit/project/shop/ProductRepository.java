package com.wellit.project.shop;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ProductRepository extends JpaRepository<Product, Long> {


    /* admin : 상품 리스트 */
    // 상품명 + 카테고리
    Page<Product> findByProdNameContainingAndProdCate(String prodName, String prodCate, Pageable pageable);

    // 상품명
    Page<Product> findByProdNameContaining(String prodName, Pageable pageable);

    // 카테고리
    Page<Product> findByProdCate(String prodCate, Pageable pageable);

    // 모든 상품
    Page<Product> findAll(Pageable pageable);

    // 상품명 + 카테고리 + 상태
    Page<Product> findByProdNameContainingAndProdCateAndProdStatus(String prodName, String prodCate, ProdStatus status, Pageable pageable);

    // 상품명+상태
    Page<Product> findByProdNameContainingAndProdStatus(String prodName, ProdStatus status, Pageable pageable);

    // 카테고리+상태
    Page<Product> findByProdCateAndProdStatus(String prodCate, ProdStatus status, Pageable pageable);

    // 모든 상품+상태
    Page<Product> findByProdStatus(ProdStatus status, Pageable pageable);









    /* shop : 상품 페이지 리스트 */
    // Product : 카테고리, 정렬조건, 페이지 Storage Procedure
    @Procedure(name = "Product.findProductsByCriteria")
    List<Product> findProductsByCriteria(
            @Param("p_category") String category,
            @Param("p_item_sort") String itemSort,  // p_item_sort로 수정
            @Param("p_page") int page,
            @Param("p_size") int size
    );


    // 프로시저 테스트용 코드 (모든 상품 리턴)
    @Procedure(name = "Product.findAllProducts")
    List<Product> findAllProducts();


}
