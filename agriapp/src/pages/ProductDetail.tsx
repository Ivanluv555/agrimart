import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Row,
  Col,
  Button,
  InputNumber,
  Descriptions,
  Tag,
  Rate,
  message,
  Divider,
  Tabs,
  List,
  Avatar,
  Empty,
  Spin,
} from 'antd';
import { ShoppingCartOutlined, ShopOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { getProductById } from '../api/product';
import { getProductReviews } from '../api/review';
import { useCart } from '../contexts/CartContext';
import { useAuth } from '../contexts/AuthContext';
import type { Product, Review } from '../types';
import { PRODUCT_CATEGORY_MAP } from '../utils/constants';
import { formatPrice, formatDateTime } from '../utils/format';

const ProductDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();

  const [product, setProduct] = useState<Product | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    if (id) {
      fetchProductDetail();
      fetchReviews();
    }
  }, [id]);

  const fetchProductDetail = async () => {
    try {
      const response = await getProductById(Number(id));
      setProduct(response.data);
    } catch (error: any) {
      message.error('获取商品详情失败');
      navigate('/products');
    } finally {
      setLoading(false);
    }
  };

  const fetchReviews = async () => {
    try {
      const response = await getProductReviews(Number(id), 1, 10);
      setReviews(response.data.content);
    } catch (error: any) {
      console.error('获取评价失败', error);
    }
  };

  const handleAddToCart = () => {
    if (!product) return;

    if (product.stock < quantity) {
      message.warning('库存不足');
      return;
    }

    addItem(product, quantity);
    message.success(`已添加 ${quantity} 件商品到购物车`);
  };

  const handleBuyNow = () => {
    if (!isAuthenticated) {
      message.warning('请先登录');
      navigate('/login');
      return;
    }

    if (!product) return;

    if (product.stock < quantity) {
      message.warning('库存不足');
      return;
    }

    addItem(product, quantity);
    navigate('/cart');
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!product) {
    return (
      <Empty description="商品不存在" style={{ marginTop: 100 }}>
        <Button type="primary" onClick={() => navigate('/products')}>
          返回商品列表
        </Button>
      </Empty>
    );
  }

  return (
    <div style={{ padding: '24px' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/products')}
        style={{ marginBottom: 16 }}
      >
        返回列表
      </Button>

      <Card>
        <Row gutter={32}>
          <Col xs={24} md={10}>
            <div
              style={{
                width: '100%',
                height: 400,
                background: '#f5f5f5',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 120,
                borderRadius: 8,
              }}
            >
              📦
            </div>
          </Col>

          <Col xs={24} md={14}>
            <div>
              <h1 style={{ fontSize: 24, fontWeight: 600, marginBottom: 16 }}>
                {product.name}
              </h1>

              <div style={{ marginBottom: 16 }}>
                <Tag color="blue">{PRODUCT_CATEGORY_MAP[product.category]}</Tag>
                {product.stock <= 0 && <Tag color="red">售罄</Tag>}
                {product.brand && <Tag>{product.brand}</Tag>}
              </div>

              <div style={{ marginBottom: 16 }}>
                <Rate disabled value={product.rating || 0} />
                <span style={{ marginLeft: 8, color: '#999' }}>
                  {product.salesCount} 已售
                </span>
              </div>

              <div
                style={{
                  background: '#f5f5f5',
                  padding: 24,
                  borderRadius: 8,
                  marginBottom: 24,
                }}
              >
                <div style={{ fontSize: 14, color: '#999', marginBottom: 8 }}>价格</div>
                <div style={{ fontSize: 32, color: '#ff4d4f', fontWeight: 600 }}>
                  {formatPrice(product.price)}
                  <span style={{ fontSize: 14, color: '#999', marginLeft: 8 }}>
                    / {product.unit}
                  </span>
                </div>
              </div>

              <Descriptions column={1} bordered>
                <Descriptions.Item label="库存">{product.stock}</Descriptions.Item>
                <Descriptions.Item label="规格">{product.specification || '-'}</Descriptions.Item>
                <Descriptions.Item label="商家">{product.merchantName}</Descriptions.Item>
              </Descriptions>

              <Divider />

              <div style={{ marginBottom: 24 }}>
                <span style={{ marginRight: 16 }}>数量：</span>
                <InputNumber
                  min={1}
                  max={product.stock}
                  value={quantity}
                  onChange={(value) => setQuantity(value || 1)}
                  disabled={product.stock <= 0}
                />
                <span style={{ marginLeft: 16, color: '#999' }}>库存 {product.stock} 件</span>
              </div>

              <Row gutter={16}>
                <Col span={12}>
                  <Button
                    size="large"
                    icon={<ShoppingCartOutlined />}
                    onClick={handleAddToCart}
                    disabled={product.stock <= 0}
                    block
                  >
                    加入购物车
                  </Button>
                </Col>
                <Col span={12}>
                  <Button
                    type="primary"
                    size="large"
                    onClick={handleBuyNow}
                    disabled={product.stock <= 0}
                    block
                  >
                    立即购买
                  </Button>
                </Col>
              </Row>
            </div>
          </Col>
        </Row>
      </Card>

      <Card style={{ marginTop: 24 }}>
        <Tabs defaultActiveKey="description">
          <Tabs.TabPane tab="商品详情" key="description">
            <div style={{ padding: 24, minHeight: 200 }}>
              <p style={{ fontSize: 16, lineHeight: 1.8, whiteSpace: 'pre-wrap' }}>
                {product.description || '暂无详情'}
              </p>
            </div>
          </Tabs.TabPane>

          <Tabs.TabPane tab="使用指南" key="guide">
            <div style={{ padding: 24, minHeight: 200 }}>
              <p style={{ fontSize: 16, lineHeight: 1.8, whiteSpace: 'pre-wrap' }}>
                {product.usageGuide || '暂无使用指南'}
              </p>
            </div>
          </Tabs.TabPane>

          <Tabs.TabPane tab={`用户评价 (${reviews.length})`} key="reviews">
            <div style={{ padding: 24 }}>
              {reviews.length === 0 ? (
                <Empty description="暂无评价" />
              ) : (
                <List
                  dataSource={reviews}
                  renderItem={(review) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={<Avatar>{review.username[0]}</Avatar>}
                        title={
                          <div>
                            <span>{review.username}</span>
                            <Rate
                              disabled
                              value={review.rating}
                              style={{ marginLeft: 16, fontSize: 14 }}
                            />
                          </div>
                        }
                        description={
                          <div>
                            <div style={{ marginBottom: 8 }}>{review.content}</div>
                            {review.reply && (
                              <div
                                style={{
                                  background: '#f5f5f5',
                                  padding: 12,
                                  borderRadius: 4,
                                  marginTop: 8,
                                }}
                              >
                                <ShopOutlined /> 商家回复：{review.reply}
                              </div>
                            )}
                            <div style={{ color: '#999', fontSize: 12, marginTop: 8 }}>
                              {formatDateTime(review.createdAt)}
                            </div>
                          </div>
                        }
                      />
                    </List.Item>
                  )}
                />
              )}
            </div>
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  );
};

export default ProductDetail;
