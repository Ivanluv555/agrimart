package edu.swu.agridemo.module.review.controller;

import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.core.result.Result;
import edu.swu.agridemo.module.review.dto.ReviewCreateDto;
import edu.swu.agridemo.module.review.dto.ReviewVo;
import edu.swu.agridemo.module.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@Tag(name = "评价管理", description = "商品评价、评分")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @Operation(summary = "创建评价")
    public Result<ReviewVo> createReview(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Valid @RequestBody ReviewCreateDto dto) {
        return Result.success(reviewService.createReview(userId, dto));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "获取商品评价列表")
    public Result<PageResult<ReviewVo>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.getProductReviews(productId, page, size));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户评价列表")
    public Result<PageResult<ReviewVo>> getUserReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.getUserReviews(userId, page, size));
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "商家回复评价")
    public Result<Void> replyReview(
            @PathVariable Long id,
            @RequestParam String reply) {
        reviewService.replyReview(id, reply);
        return Result.success();
    }
}
