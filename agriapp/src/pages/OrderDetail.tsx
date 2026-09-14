import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Descriptions, Table, Tag, Button, Steps, message, Modal, Form, Input, Rate, Spin, Empty } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { getOrderById, cancelOrder, updateOrderStatus } from '../api/order';
import { createReview } from '../api/review';
import { useAuth } from '../contexts/AuthContext';
import type { Order, OrderStatus as OrderStatusEnum } from '../types';
import { ORDER_STATUS_MAP, ORDER_STATUS_COLOR_MAP } from '../utils/constants';
import { formatPrice, formatDateTime } from '../utils/format';
import { UserRole } from '../types';

const OrderDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [reviewModalVisible, setReviewModalVisible] = useState(false);
  const [reviewForm] = Form.useForm();
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);

  useEffect(() => {
    if (id) {
      fetchOrderDetail();
    }
  }, [id]);

  const fetchOrderDetail = async () => {
    try {
      const response = await getOrderById(Number(id));
      setOrder(response.data);
    } catch (error: any) {
      message.error('获取订单详情失败');
      navigate('/orders');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async () => {
    if (!order || !user) return;

    Modal.confirm({
      title: '确认取消订单？',
      content: '取消后订单将无法恢复',
      onOk: async () => {
        try {
          await cancelOrder(order.id, user.id);
          message.success('订单已取消');
          fetchOrderDetail();
        } catch (error: any) {
          message.error(error.message || '取消订单失败');
        }
      },
    });
  };

  const handleUpdateStatus = async (status: OrderStatusEnum) => {
    if (!order) return;

    try {
      await updateOrderStatus(order.id, status);
      message.success('订单状态已更新');
      fetchOrderDetail();
    } catch (error: any) {
      message.error(error.message || '更新订单状态失败');
    }
  };

  const handleReview = (productId: number) => {
    setSelectedProductId(productId);
    setReviewModalVisible(true);
  };

  const handleSubmitReview = async (values: any) => {
    if (!order || !user || !selectedProductId) return;

    try {
      await createReview(user.id, {
        orderId: order.id,
        productId: selectedProductId,
        rating: values.rating,
        content: values.content,
      });
      message.success('评价成功！');
      setReviewModalVisible(false);
      reviewForm.resetFields();
    } catch (error: any) {
      message.error(error.message || '评价失败');
    }
  };

  const getOrderSteps = () => {
    if (!order) return 0;
    const statusOrder = [
      'PENDING_PAYMENT',
      'PAID',
      'SHIPPED',
      'DELIVERED',
      'COMPLETED',
    ] as const;
    return statusOrder.indexOf(order.status as any);
  };

  const columns = [
    {
      title: '商品信息',
      dataIndex: 'productName',
      key: 'productName',
      render: (text: string) => (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div
            style={{
              width: 60,
              height: 60,
              background: '#f5f5f5',
              borderRadius: 8,
              marginRight: 12,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 24,
            }}
          >
            📦
          </div>
          <span>{text}</span>
        </div>
      ),
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
      dataIndex: 'subtotal',
      key: 'subtotal',
      render: (subtotal: number) => (
        <span style={{ color: '#ff4d4f', fontWeight: 600 }}>{formatPrice(subtotal)}</span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => {
        if (order?.status === 'COMPLETED' && user?.role === UserRole.FARMER) {
          return (
            <Button type="link" onClick={() => handleReview(record.productId)}>
              评价
            </Button>
          );
        }
        return null;
      },
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!order) {
    return (
      <Empty description="订单不存在" style={{ marginTop: 100 }}>
        <Button type="primary" onClick={() => navigate('/orders')}>
          返回订单列表
        </Button>
      </Empty>
    );
  }

  return (
    <div style={{ padding: '24px' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/orders')}
        style={{ marginBottom: 16 }}
      >
        返回列表
      </Button>

      <Card
        title={
          <div>
            订单详情
            <Tag color={ORDER_STATUS_COLOR_MAP[order.status]} style={{ marginLeft: 16 }}>
              {ORDER_STATUS_MAP[order.status]}
            </Tag>
          </div>
        }
      >
        <Steps
          current={getOrderSteps()}
          items={[
            { title: '待支付' },
            { title: '已支付' },
            { title: '已发货' },
            { title: '已送达' },
            { title: '已完成' },
          ]}
          style={{ marginBottom: 32 }}
        />

        <Descriptions bordered column={2}>
          <Descriptions.Item label="订单编号">{order.orderNo}</Descriptions.Item>
          <Descriptions.Item label="下单时间">{formatDateTime(order.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="收货人">{order.receiverName}</Descriptions.Item>
          <Descriptions.Item label="联系电话">{order.receiverPhone}</Descriptions.Item>
          <Descriptions.Item label="收货地址" span={2}>{order.shippingAddress}</Descriptions.Item>
          {order.remark && (
            <Descriptions.Item label="订单备注" span={2}>{order.remark}</Descriptions.Item>
          )}
          {order.trackingNumber && (
            <Descriptions.Item label="物流单号" span={2}>{order.trackingNumber}</Descriptions.Item>
          )}
        </Descriptions>

        <div style={{ marginTop: 24 }}>
          <Table
            dataSource={order.orderItems}
            columns={columns}
            rowKey="id"
            pagination={false}
          />
        </div>

        <div style={{ textAlign: 'right', marginTop: 24, fontSize: 18 }}>
          订单总额：
          <span style={{ color: '#ff4d4f', fontSize: 24, fontWeight: 600, marginLeft: 8 }}>
            {formatPrice(order.totalAmount)}
          </span>
        </div>

        <div style={{ marginTop: 24, textAlign: 'right' }}>
          {order.status === 'PENDING_PAYMENT' && user?.role === UserRole.FARMER && (
            <>
              <Button onClick={handleCancelOrder} style={{ marginRight: 8 }}>
                取消订单
              </Button>
              <Button type="primary" onClick={() => handleUpdateStatus('PAID' as OrderStatusEnum)}>
                确认支付
              </Button>
            </>
          )}
          {order.status === 'PAID' && user?.role === UserRole.MERCHANT && (
            <Button type="primary" onClick={() => handleUpdateStatus('SHIPPED' as OrderStatusEnum)}>
              确认发货
            </Button>
          )}
          {order.status === 'SHIPPED' && user?.role === UserRole.FARMER && (
            <Button type="primary" onClick={() => handleUpdateStatus('DELIVERED' as OrderStatusEnum)}>
              确认收货
            </Button>
          )}
          {order.status === 'DELIVERED' && user?.role === UserRole.FARMER && (
            <Button type="primary" onClick={() => handleUpdateStatus('COMPLETED' as OrderStatusEnum)}>
              完成订单
            </Button>
          )}
        </div>
      </Card>

      <Modal
        title="评价商品"
        open={reviewModalVisible}
        onCancel={() => {
          setReviewModalVisible(false);
          reviewForm.resetFields();
        }}
        footer={null}
      >
        <Form form={reviewForm} onFinish={handleSubmitReview} layout="vertical">
          <Form.Item
            label="评分"
            name="rating"
            rules={[{ required: true, message: '请选择评分' }]}
            initialValue={5}
          >
            <Rate />
          </Form.Item>

          <Form.Item
            label="评价内容"
            name="content"
            rules={[{ required: true, message: '请输入评价内容' }]}
          >
            <Input.TextArea rows={4} placeholder="分享您的使用体验" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              提交评价
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default OrderDetail;
