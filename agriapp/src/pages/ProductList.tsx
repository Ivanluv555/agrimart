import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Input, Select, Pagination, message, Tag, Rate, Button, Empty, Spin } from 'antd';
import { ShoppingCartOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { searchProducts } from '../api/product';
import { useCart } from '../contexts/CartContext';
import type { Product, ProductCategory } from '../types';
import { PRODUCT_CATEGORY_MAP } from '../utils/constants';
import { formatPrice } from '../utils/format';

const { Option } = Select;

const ProductList: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<ProductCategory | undefined>();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(12);
  const [total, setTotal] = useState(0);

  const navigate = useNavigate();
  const { addItem } = useCart();

  useEffect(() => {
    fetchProducts();
  }, [category, currentPage]);

  const fetchProducts = async () => {
    setLoading(true);
    try {
      const response = await searchProducts({
        category,
        keyword: keyword || undefined,
        page: currentPage,
        size: pageSize,
      });
      setProducts(response.data.content);
      setTotal(response.data.totalElements);
    } catch (error: any) {
      message.error('获取商品列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    setCurrentPage(1);
    fetchProducts();
  };

  const handleAddToCart = (product: Product, e: React.MouseEvent) => {
    e.stopPropagation();
    if (product.stock <= 0) {
      message.warning('商品已售罄');
      return;
    }
    addItem(product, 1);
    message.success('已添加到购物车');
  };

  const handleProductClick = (productId: number) => {
    navigate(`/products/${productId}`);
  };

  return (
    <div style={{ padding: '24px' }}>
      <Card style={{ marginBottom: 24 }}>
        <Row gutter={16}>
          <Col flex="auto">
            <Input
              size="large"
              placeholder="搜索商品名称"
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={handleSearch}
            />
          </Col>
          <Col>
            <Select
              size="large"
              style={{ width: 150 }}
              placeholder="商品分类"
              allowClear
              value={category}
              onChange={(value) => {
                setCategory(value);
                setCurrentPage(1);
              }}
            >
              {Object.entries(PRODUCT_CATEGORY_MAP).map(([key, label]) => (
                <Option key={key} value={key}>
                  {label}
                </Option>
              ))}
            </Select>
          </Col>
          <Col>
            <Button type="primary" size="large" onClick={handleSearch}>
              搜索
            </Button>
          </Col>
        </Row>
      </Card>

      <Spin spinning={loading}>
        {products.length === 0 && !loading ? (
          <Empty description="暂无商品" style={{ marginTop: 60 }} />
        ) : (
          <>
            <Row gutter={[16, 16]}>
              {products.map((product) => (
                <Col key={product.id} xs={24} sm={12} md={8} lg={6}>
                  <Card
                    hoverable
                    cover={
                      <div
                        style={{
                          height: 200,
                          background: '#f5f5f5',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 48,
                          color: '#999',
                        }}
                      >
                        📦
                      </div>
                    }
                    onClick={() => handleProductClick(product.id)}
                  >
                    <div style={{ marginBottom: 8 }}>
                      <Tag color="blue">{PRODUCT_CATEGORY_MAP[product.category]}</Tag>
                      {product.stock <= 0 && <Tag color="red">售罄</Tag>}
                    </div>
                    <Card.Meta
                      title={
                        <div style={{ fontSize: 14, fontWeight: 500, minHeight: 40 }}>
                          {product.name}
                        </div>
                      }
                      description={
                        <div>
                          <div style={{ marginBottom: 8 }}>
                            <Rate disabled value={product.rating || 0} style={{ fontSize: 14 }} />
                            <span style={{ marginLeft: 8, color: '#999', fontSize: 12 }}>
                              {product.salesCount} 已售
                            </span>
                          </div>
                          <div
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                            }}
                          >
                            <span style={{ color: '#ff4d4f', fontSize: 20, fontWeight: 600 }}>
                              {formatPrice(product.price)}
                            </span>
                            <Button
                              type="primary"
                              icon={<ShoppingCartOutlined />}
                              onClick={(e) => handleAddToCart(product, e)}
                              disabled={product.stock <= 0}
                            >
                              加购
                            </Button>
                          </div>
                        </div>
                      }
                    />
                  </Card>
                </Col>
              ))}
            </Row>

            {total > pageSize && (
              <div style={{ marginTop: 24, textAlign: 'center' }}>
                <Pagination
                  current={currentPage}
                  pageSize={pageSize}
                  total={total}
                  onChange={setCurrentPage}
                  showSizeChanger={false}
                  showTotal={(total) => `共 ${total} 件商品`}
                />
              </div>
            )}
          </>
        )}
      </Spin>
    </div>
  );
};

export default ProductList;
