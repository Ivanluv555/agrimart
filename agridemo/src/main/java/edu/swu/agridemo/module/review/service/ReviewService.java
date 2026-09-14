package edu.swu.agridemo.module.review.service;

import edu.swu.agridemo.core.exception.BusinessException;
import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.module.order.entity.Order;
import edu.swu.agridemo.module.order.repository.OrderRepository;
import edu.swu.agridemo.module.product.entity.Product;
import edu.swu.agridemo.module.product.repository.ProductRepository;
import edu.swu.agridemo.module.review.dto.ReviewCreateDto;
import edu.swu.agridemo.module.review.dto.ReviewVo;
import edu.swu.agridemo.module.review.entity.Review;
import edu.swu.agridemo.module.review.repository.ReviewRepository;
import edu.swu.agridemo.module.user.entity.User;
import edu.swu.agridemo.module.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository,
                        ProductRepository productRepository, OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ReviewVo createReview(Long userId, ReviewCreateDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Order order = orderRepository.findById(dto.orderId())
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException("无权评价此订单");
        }

        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            throw new BusinessException("订单未完成，无法评价");
        }

        Product product = productRepository.findById(dto.productId())
                .orElseThrow(() -> new BusinessException("商品不存在"));

        if (reviewRepository.existsByOrderIdAndUserId(dto.orderId(), userId)) {
            throw new BusinessException("已评价过该订单");
        }

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setOrder(order);
        review.setRating(dto.rating());
        review.setContent(dto.content());
        review.setImages(dto.images());

        review = reviewRepository.save(review);

        updateProductRating(product.getId());

        return convertToVo(review);
    }

    public PageResult<ReviewVo> getProductReviews(Long productId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewPage = reviewRepository.findByProductId(productId, pageable);

        List<ReviewVo> records = reviewPage.getContent().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return PageResult.of(records, reviewPage.getTotalElements(), page, size);
    }

    public PageResult<ReviewVo> getUserReviews(Long userId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewPage = reviewRepository.findByUserId(userId, pageable);

        List<ReviewVo> records = reviewPage.getContent().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return PageResult.of(records, reviewPage.getTotalElements(), page, size);
    }

    @Transactional
    public void replyReview(Long reviewId, String reply) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("评价不存在"));
        review.setMerchantReply(reply);
        reviewRepository.save(review);
    }

    private void updateProductRating(Long productId) {
        Double avgRating = reviewRepository.calculateAverageRating(productId);
        if (avgRating != null) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException("商品不存在"));
            product.setRating(BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP));
            productRepository.save(product);
        }
    }

    private ReviewVo convertToVo(Review review) {
        return new ReviewVo(
            review.getId(),
            review.getUser().getId(),
            review.getUser().getUsername(),
            review.getUser().getAvatar(),
            review.getProduct().getId(),
            review.getProduct().getName(),
            review.getOrder().getId(),
            review.getRating(),
            review.getContent(),
            review.getImages(),
            review.getMerchantReply(),
            review.getCreatedAt()
        );
    }
}
