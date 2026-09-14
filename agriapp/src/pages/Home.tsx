import React from 'react';
import { Row, Col, Card, Button, Typography, Statistic } from 'antd';
import { ShoppingOutlined, RocketOutlined, SafetyOutlined, CustomerServiceOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const { Title, Paragraph } = Typography;

const Home: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const features = [
    {
      icon: <ShoppingOutlined style={{ fontSize: 48, color: '#667eea' }} />,
      title: '正品农资',
      description: '厂家直供，减少中间环节，保证正品质量',
    },
    {
      icon: <RocketOutlined style={{ fontSize: 48, color: '#667eea' }} />,
      title: '快速配送',
      description: '专业物流配送，确保农资及时送达',
    },
    {
      icon: <SafetyOutlined style={{ fontSize: 48, color: '#667eea' }} />,
      title: '品质保障',
      description: '严格质检，假一赔十，保障农户权益',
    },
    {
      icon: <CustomerServiceOutlined style={{ fontSize: 48, color: '#667eea' }} />,
      title: '专业服务',
      description: '农技专家在线指导，解决种植难题',
    },
  ];

  const categories = [
    { name: '化肥', icon: '🌾', color: '#52c41a' },
    { name: '种子', icon: '🌱', color: '#1890ff' },
    { name: '农药', icon: '💊', color: '#faad14' },
    { name: '农具', icon: '🔧', color: '#722ed1' },
  ];

  return (
    <div>
      {/* Hero Section */}
      <div
        style={{
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          padding: '80px 50px',
          color: '#fff',
        }}
      >
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>
          <Row gutter={[32, 32]} align="middle">
            <Col xs={24} md={12}>
              <Title style={{ color: '#fff', fontSize: 48, marginBottom: 24 }}>
                农惠达
              </Title>
              <Paragraph style={{ color: '#fff', fontSize: 20, marginBottom: 32 }}>
                专业的农资电商平台，为农户提供正品农资在线采购和农机租赁服务
              </Paragraph>
              <div style={{ display: 'flex', gap: 16 }}>
                <Button
                  type="primary"
                  size="large"
                  onClick={() => navigate('/products')}
                  style={{ background: '#fff', color: '#667eea', border: 'none', height: 50, padding: '0 40px' }}
                >
                  立即选购
                </Button>
                {!isAuthenticated && (
                  <Button
                    size="large"
                    onClick={() => navigate('/login')}
                    style={{ background: 'transparent', color: '#fff', borderColor: '#fff', height: 50, padding: '0 40px' }}
                  >
                    注册账号
                  </Button>
                )}
              </div>
            </Col>
            <Col xs={24} md={12}>
              <Row gutter={[16, 16]}>
                <Col span={12}>
                  <Card style={{ textAlign: 'center' }}>
                    <Statistic title="商品种类" value={1000} suffix="+" />
                  </Card>
                </Col>
                <Col span={12}>
                  <Card style={{ textAlign: 'center' }}>
                    <Statistic title="服务用户" value={10000} suffix="+" />
                  </Card>
                </Col>
                <Col span={12}>
                  <Card style={{ textAlign: 'center' }}>
                    <Statistic title="合作商家" value={500} suffix="+" />
                  </Card>
                </Col>
                <Col span={12}>
                  <Card style={{ textAlign: 'center' }}>
                    <Statistic title="好评率" value={98} suffix="%" />
                  </Card>
                </Col>
              </Row>
            </Col>
          </Row>
        </div>
      </div>

      {/* Categories Section */}
      <div style={{ padding: '60px 50px', maxWidth: 1200, margin: '0 auto' }}>
        <Title level={2} style={{ textAlign: 'center', marginBottom: 40 }}>
          热门分类
        </Title>
        <Row gutter={[24, 24]}>
          {categories.map((category, index) => (
            <Col key={index} xs={12} sm={6}>
              <Card
                hoverable
                style={{ textAlign: 'center', cursor: 'pointer' }}
                onClick={() => navigate('/products')}
              >
                <div style={{ fontSize: 64, marginBottom: 16 }}>{category.icon}</div>
                <Title level={4} style={{ margin: 0, color: category.color }}>
                  {category.name}
                </Title>
              </Card>
            </Col>
          ))}
        </Row>
      </div>

      {/* Features Section */}
      <div style={{ background: '#f0f2f5', padding: '60px 50px' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>
          <Title level={2} style={{ textAlign: 'center', marginBottom: 40 }}>
            平台优势
          </Title>
          <Row gutter={[24, 24]}>
            {features.map((feature, index) => (
              <Col key={index} xs={24} sm={12} md={6}>
                <Card style={{ textAlign: 'center', height: '100%' }}>
                  <div style={{ marginBottom: 16 }}>{feature.icon}</div>
                  <Title level={4}>{feature.title}</Title>
                  <Paragraph style={{ color: '#666' }}>{feature.description}</Paragraph>
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </div>

      {/* CTA Section */}
      <div style={{ padding: '60px 50px', textAlign: 'center' }}>
        <div style={{ maxWidth: 800, margin: '0 auto' }}>
          <Title level={2}>立即开始</Title>
          <Paragraph style={{ fontSize: 18, color: '#666', marginBottom: 32 }}>
            加入农惠达，享受便捷的农资采购服务
          </Paragraph>
          <Button
            type="primary"
            size="large"
            onClick={() => navigate('/products')}
            style={{ height: 50, padding: '0 60px', fontSize: 18 }}
          >
            浏览商品
          </Button>
        </div>
      </div>
    </div>
  );
};

export default Home;
