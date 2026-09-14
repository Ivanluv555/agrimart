import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Tabs } from 'antd';
import { UserOutlined, LockOutlined, PhoneOutlined, MailOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { login, register } from '../api/user';
import type { LoginForm, RegisterForm } from '../types';
import { UserRole } from '../types';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login: authLogin } = useAuth();

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const response = await login(values);
      authLogin(response.data);
      message.success('登录成功！');
      navigate('/');
    } catch (error: any) {
      message.error(error.message || '登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: RegisterForm) => {
    setLoading(true);
    try {
      const response = await register(values);
      authLogin(response.data);
      message.success('注册成功！');
      navigate('/');
    } catch (error: any) {
      message.error(error.message || '注册失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
    }}>
      <Card style={{ width: 450, boxShadow: '0 4px 12px rgba(0,0,0,0.15)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <h1 style={{ fontSize: 28, fontWeight: 600, color: '#667eea', margin: 0 }}>
            农惠达
          </h1>
          <p style={{ color: '#999', marginTop: 8 }}>农资电商与农机共享平台</p>
        </div>

        <Tabs defaultActiveKey="login" centered>
          <Tabs.TabPane tab="登录" key="login">
            <Form onFinish={handleLogin} autoComplete="off">
              <Form.Item
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="用户名"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[{ required: true, message: '请输入密码' }]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="密码"
                  size="large"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  loading={loading}
                  block
                >
                  登录
                </Button>
              </Form.Item>

              <div style={{ textAlign: 'center', color: '#999', fontSize: 12 }}>
                测试账号：farmer / farmer123
              </div>
            </Form>
          </Tabs.TabPane>

          <Tabs.TabPane tab="注册" key="register">
            <Form onFinish={handleRegister} autoComplete="off">
              <Form.Item
                name="username"
                rules={[
                  { required: true, message: '请输入用户名' },
                  { min: 3, message: '用户名至少3个字符' }
                ]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="用户名"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[
                  { required: true, message: '请输入密码' },
                  { min: 6, message: '密码至少6个字符' }
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="密码"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="realName"
                rules={[{ required: true, message: '请输入真实姓名' }]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="真实姓名"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="phone"
                rules={[
                  { required: true, message: '请输入手机号' },
                  { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' }
                ]}
              >
                <Input
                  prefix={<PhoneOutlined />}
                  placeholder="手机号"
                  size="large"
                />
              </Form.Item>

              <Form.Item name="email">
                <Input
                  prefix={<MailOutlined />}
                  placeholder="邮箱（可选）"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="role"
                rules={[{ required: true, message: '请选择角色' }]}
                initialValue={UserRole.FARMER}
              >
                <select style={{ width: '100%', height: 40, padding: '0 11px', borderRadius: 6, border: '1px solid #d9d9d9' }}>
                  <option value={UserRole.FARMER}>农户</option>
                  <option value={UserRole.MERCHANT}>商家</option>
                  <option value={UserRole.MACHINE_OWNER}>农机拥有者</option>
                </select>
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  loading={loading}
                  block
                >
                  注册
                </Button>
              </Form.Item>
            </Form>
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  );
};

export default Login;
