import React from 'react';
import { Layout, Menu, Badge, Dropdown, Avatar, Button } from 'antd';
import {
  HomeOutlined,
  ShoppingOutlined,
  ShoppingCartOutlined,
  FileTextOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useCart } from '../contexts/CartContext';

const { Header, Content, Footer } = Layout;

const MainLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, isAuthenticated } = useAuth();
  const { totalItems } = useCart();

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: '首页',
    },
    {
      key: '/products',
      icon: <ShoppingOutlined />,
      label: '商品',
    },
    {
      key: '/orders',
      icon: <FileTextOutlined />,
      label: '订单',
    },
  ];

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        logout();
        navigate('/login');
      },
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 999,
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          background: '#fff',
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          padding: '0 50px',
        }}
      >
        <div
          style={{
            fontSize: 24,
            fontWeight: 600,
            color: '#667eea',
            marginRight: 50,
            cursor: 'pointer',
          }}
          onClick={() => navigate('/')}
        >
          农惠达
        </div>

        <Menu
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, border: 'none' }}
        />

        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <Badge count={totalItems} offset={[0, 0]}>
            <Button
              type="text"
              icon={<ShoppingCartOutlined style={{ fontSize: 20 }} />}
              onClick={() => navigate('/cart')}
            />
          </Badge>

          {isAuthenticated && user ? (
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar icon={<UserOutlined />} src={user.avatar} />
                <span>{user.realName}</span>
              </div>
            </Dropdown>
          ) : (
            <Button type="primary" onClick={() => navigate('/login')}>
              登录
            </Button>
          )}
        </div>
      </Header>

      <Content style={{ background: '#f0f2f5' }}>
        <Outlet />
      </Content>

      <Footer style={{ textAlign: 'center', background: '#fff' }}>
        农惠达 © 2026 - 农资电商与农机共享平台
      </Footer>
    </Layout>
  );
};

export default MainLayout;
