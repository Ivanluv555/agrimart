import React, { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Select, message, Empty } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getUserOrders } from '../api/order';
import { useAuth } from '../contexts/AuthContext';
import type { Order, OrderStatus } from '../types';
import { ORDER_STATUS_MAP, ORDER_STATUS_COLOR_MAP } from '../utils/constants';
import { formatPrice, formatDateTime } from '../utils/format';

const { Option } = Select;

const OrderList: React.FC = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<OrderStatus | undefined>();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const navigate = useNavigate();
  const { user } = useAuth();

  useEffect(() => {
    if (user) {
      fetchOrders();
    }
  }, [user, status, currentPage]);

  const fetchOrders = async () => {
    if (!user) return;

    setLoading(true);
    try {
      const response = await getUserOrders(user.id, status, currentPage, pageSize);
      setOrders(response.data.content);
      setTotal(response.data.totalElements);
    } catch (error: any) {
      message.error('获取订单列表失败');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 200,
    },
    {
      title: '商品信息',
      dataIndex: 'orderItems',
      key: 'orderItems',
      render: (items: any[]) => (
        <div>
          {items.map((item, index) => (
            <div key={index} style={{ marginBottom: 4 }}>
              {item.productName} × {item.quantity}
            </div>
          ))}
        </div>
      ),
    },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      render: (amount: number) => (
        <span style={{ color: '#ff4d4f', fontWeight: 600 }}>{formatPrice(amount)}</span>
      ),
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: OrderStatus) => (
        <Tag color={ORDER_STATUS_COLOR_MAP[status]}>{ORDER_STATUS_MAP[status]}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (time: string) => formatDateTime(time),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: Order) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/orders/${record.id}`)}
        >
          查看
        </Button>
      ),
    },
  ];

  if (!user) {
    return (
      <div style={{ padding: '24px' }}>
        <Card>
          <Empty description="请先登录" />
        </Card>
      </div>
    );
  }

  return (
    <div style={{ padding: '24px' }}>
      <Card
        title="我的订单"
        extra={
          <Select
            style={{ width: 150 }}
            placeholder="订单状态"
            allowClear
            value={status}
            onChange={(value) => {
              setStatus(value);
              setCurrentPage(1);
            }}
          >
            {Object.entries(ORDER_STATUS_MAP).map(([key, label]) => (
              <Option key={key} value={key}>
                {label}
              </Option>
            ))}
          </Select>
        }
      >
        <Table
          dataSource={orders}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: total,
            onChange: setCurrentPage,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Card>
    </div>
  );
};

export default OrderList;
