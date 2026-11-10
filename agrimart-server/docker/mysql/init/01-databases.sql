-- 本地开发使用一个 MySQL 实例；每个服务仅拥有自己的逻辑库。
CREATE DATABASE IF NOT EXISTS agrimart_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS agrimart_trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS agrimart_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'agrimart_user'@'%' IDENTIFIED BY 'agrimart_user_dev';
CREATE USER IF NOT EXISTS 'agrimart_trade'@'%' IDENTIFIED BY 'agrimart_trade_dev';
CREATE USER IF NOT EXISTS 'agrimart_payment'@'%' IDENTIFIED BY 'agrimart_payment_dev';

GRANT ALL PRIVILEGES ON agrimart_user.* TO 'agrimart_user'@'%';
GRANT ALL PRIVILEGES ON agrimart_trade.* TO 'agrimart_trade'@'%';
GRANT ALL PRIVILEGES ON agrimart_payment.* TO 'agrimart_payment'@'%';
