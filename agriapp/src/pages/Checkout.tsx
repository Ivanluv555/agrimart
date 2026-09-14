import React, { useState } from 'react';
import { Card, Form, Input, Button, Table, message, Row, Col, Divider } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../contexts/CartContext';
import { useAuth } from '../contexts/AuthContext';
import { createOrder } from '../api/order';
import { formatPrice } from '../utils/format';
import type { OrderCreateForm } from '../types';

const Checkout: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { items, totalAmount, clearCart } = useCart();
  const { user } = useAuth();

  const handleSubmit = async (values: any) => {
    if (!user) {
      message.warning('请先登录');
      navigate('/login');
      return;
    }

    if (items.length === 0) {
      message.warning('购物车为空');
      navigate('/cart');
      return;
    }

    setLoading(true);
    try {
      const orderData: OrderCreateForm = {
        items: items.map((item) => ({
          productId: item.id,
          quantity: item.quantity,
        })),
        shippingAddress: values.shippingAddress,
        receiverName: values.receiverName,
        receiverPhone: values.receiverPhone,
        remark: values.remark,
      };

      const response = await createOrder(user.id, orderData);
      message.success('订单创建成功！');
      clearCart();
      navigate(`/orders/${response.data.id}`);
    } catch (error: any) {
      message.error(error.message || '订单创建失败');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    {
      title: '商品',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '单价',
      dataIndex: 'price',
      key: 'price',
      render: (price: number) => formatPrice(price),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
    },
    {
      title: '小计',
      key: 'subtotal',
      render: (_: any, record: any) => formatPrice(record.price * record.quantity),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Row gutter={24}>
        <Col xs={24} lg={14}>
          <Card title="收货信息" style={{ marginBottom: 24 }}>
            <Form
              form={form}
              layout="vertical"
              onFinish={handleSubmit}
              initialValues={{
                receiverName: user?.realName,
                receiverPhone: user?.phone,
                shippingAddress: user?.address,
              }}
            >
              <Form.Item
                label="收货人"
                name="receiverName"
                rules={[{ required: true, message: '请输入收货人姓名' }]}
              >
                <Input placeholder="收货人姓名" size="large" />
              </Form.Item>

              <Form.Item
                label="手机号"
                name="receiverPhone"
                rules={[
                  { required: true, message: '请输入手机号' },
                  { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' },
                ]}
              >
                <Input placeholder="手机号" size="large" />
              </Form.Item>

              <Form.Item
                label="收货地址"
                name="shippingAddress"
                rules={[{ required: true, message: '请输入收货地址' }]}
              >
                <Input.TextArea
                  placeholder="详细收货地址"
                  rows={3}
                  size="large"
                />
              </Form.Item>

              <Form.Item label="备注" name="remark">
                <Input.TextArea
                  placeholder="订单备注（选填）"
                  rows={2}
                  size="large"
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card title="订单信息">
            <Table
              dataSource={items}
              columns={columns}
              rowKey="id"
              pagination={false}
              size="small"
            />

            <Divider />

            <div style={{ marginBottom: 16 }}>
              <Row justify="space-between">
                <Col>商品总额：</Col>
                <Col>{formatPrice(totalAmount)}</Col>
              </Row>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Row justify="space-between">
                <Col>运费：</Col>
                <Col>免运费</Col>
              </Row>
            </div>

            <Divider />

            <div style={{ marginBottom: 24 }}>
              <Row justify="space-between" style={{ fontSize: 18, fontWeight: 600 }}>
                <Col>应付总额：</Col>
                <Col style={{ color: '#ff4d4f', fontSize: 24 }}>
                  {formatPrice(totalAmount)}
                </Col>
              </Row>
            </div>

            <Button
              type="primary"
              size="large"
              loading={loading}
              onClick={() => form.submit()}
              block
            >
              提交订单
            </Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Checkout;
