package edu.swu.agridemo.module.product.service;

import edu.swu.agridemo.core.exception.BusinessException;
import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.module.product.dto.ProductCreateDto;
import edu.swu.agridemo.module.product.dto.ProductVo;
import edu.swu.agridemo.module.product.entity.Product;
import edu.swu.agridemo.module.product.repository.ProductRepository;
import edu.swu.agridemo.module.user.entity.User;
import edu.swu.agridemo.module.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProductVo createProduct(Long merchantId, ProductCreateDto dto) {
        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException("商家不存在"));

        Product product = new Product();
        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setCategory(dto.category());
        product.setPrice(dto.price());
        product.setStock(dto.stock());
        product.setUnit(dto.unit());
        product.setBrand(dto.brand());
        product.setSpecification(dto.specification());
        product.setImages(dto.images());
        product.setUsageGuide(dto.usageGuide());
        product.setMerchant(merchant);
        product.setStatus(Product.ProductStatus.ON_SALE);

        product = productRepository.save(product);
        return convertToVo(product);
    }

    public ProductVo getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        return convertToVo(product);
    }

    public PageResult<ProductVo> searchProducts(Product.ProductCategory category, String keyword,
                                                Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> productPage = productRepository.searchProducts(
                Product.ProductStatus.ON_SALE, category, keyword, pageable);

        List<ProductVo> records = productPage.getContent().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return PageResult.of(records, productPage.getTotalElements(), page, size);
    }

    public PageResult<ProductVo> getProductsByMerchant(Long merchantId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> productPage = productRepository.findByMerchantId(merchantId, pageable);

        List<ProductVo> records = productPage.getContent().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return PageResult.of(records, productPage.getTotalElements(), page, size);
    }

    @Transactional
    public void updateStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("商品不存在"));

        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new BusinessException("库存不足");
        }

        product.setStock(newStock);
        productRepository.save(product);
    }

    @Transactional
    public void updateProductStatus(Long productId, Product.ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        product.setStatus(status);
        productRepository.save(product);
    }

    private ProductVo convertToVo(Product product) {
        return new ProductVo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getCategory(),
            product.getPrice(),
            product.getStock(),
            product.getUnit(),
            product.getBrand(),
            product.getSpecification(),
            product.getImages(),
            product.getStatus(),
            product.getMerchant().getId(),
            product.getMerchant().getRealName(),
            product.getSalesCount(),
            product.getRating(),
            product.getUsageGuide(),
            product.getCreatedAt()
        );
    }
}
