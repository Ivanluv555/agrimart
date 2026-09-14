import React from 'react';
import { Card, Table, Button, InputNumber, Empty, message, Row, Col } from 'antd';
import { DeleteOutlined, ShoppingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../contexts/CartContext';
import { useAuth } from '../contexts/AuthContext';
import { formatPrice } from '../utils/format';

const Cart: React.FC = () => {
  const navigate = useNavigate();
  const { items, removeItem, updateQuantity, totalAmount, clearCart } = useCart();
  const { isAuthenticated } = useAuth();

  const handleCheckout = () => {
    if (!isAuthenticated) {
      message.warning('请先登录');
      navigate('/login');
      return;
    }

    if (items.length === 0) {
      message.warning('购物车为空');
      return;
    }

    navigate('/checkout');
  };

  const columns = [
    {
      title: '商品信息',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: any) => (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div
            style={{
              width: 80,
              height: 80,
              background: '#f5f5f5',
              borderRadius: 8,
              marginRight: 16,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 32,
            }}
          >
            📦
          </div>
          <div>
            <div style={{ fontWeight: 500, marginBottom: 4 }}>{text}</div>
            <div style={{ color: '#999', fontSize: 12 }}>{record.specification}</div>
          </div>
        </div>
      ),
    },
    {
      title: '单价',
      dataIndex: 'price',
      key: 'price',
      width: 120,
      render: (price: number) => formatPrice(price),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 150,
      render: (quantity: number, record: any) => (
        <InputNumber
          min={1}
          max={record.stock}
          value={quantity}
          onChange={(value) => updateQuantity(record.id, value || 1)}
        />
      ),
    },
    {
      title: '小计',
      key: 'subtotal',
      width: 120,
      render: (_: any, record: any) => (
        <span style={{ color: '#ff4d4f', fontWeight: 600 }}>
          {formatPrice(record.price * record.quantity)}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: any) => (
        <Button
          type="link"
          danger
          icon={<DeleteOutlined />}
          onClick={() => {
            removeItem(record.id);
            message.success('已移除商品');
          }}
        >
          删除
        </Button>
      ),
    },
  ];

  if (items.length === 0) {
    return (
      <div style={{ padding: '24px' }}>
        <Card>
          <Empty
            description="购物车为空"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '60px 0' }}
          >
            <Button type="primary" onClick={() => navigate('/products')}>
              去逛逛
            </Button>
          </Empty>
        </Card>
      </div>
    );
  }

  return (
    <div style={{ padding: '24px' }}>
      <Card title="购物车" extra={<Button onClick={clearCart}>清空购物车</Button>}>
        <Table
          dataSource={items}
          columns={columns}
          rowKey="id"
          pagination={false}
        />

        <div style={{ marginTop: 24 }}>
          <Row justify="end">
            <Col>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: 16, marginBottom: 8 }}>
                  共 <span style={{ color: '#ff4d4f', fontWeight: 600 }}>{items.length}</span> 件商品
                </div>
                <div style={{ fontSize: 20, marginBottom: 16 }}>
                  合计：
                  <span style={{ color: '#ff4d4f', fontSize: 28, fontWeight: 600, marginLeft: 8 }}>
                    {formatPrice(totalAmount)}
                  </span>
                </div>
                <Button
                  type="primary"
                  size="large"
                  icon={<ShoppingOutlined />}
                  onClick={handleCheckout}
                  style={{ width: 200 }}
                >
                  去结算
                </Button>
              </div>
            </Col>
          </Row>
        </div>
      </Card>
    </div>
  );
};

export default Cart;
