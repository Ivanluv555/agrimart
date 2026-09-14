import dayjs from 'dayjs';

// 格式化价格
export const formatPrice = (price: number): string => {
  return `¥${price.toFixed(2)}`;
};

// 格式化日期时间
export const formatDateTime = (dateTime: string): string => {
  return dayjs(dateTime).format('YYYY-MM-DD HH:mm:ss');
};

// 格式化日期
export const formatDate = (date: string): string => {
  return dayjs(date).format('YYYY-MM-DD');
};

// 格式化相对时间
export const formatRelativeTime = (dateTime: string): string => {
  const now = dayjs();
  const target = dayjs(dateTime);
  const diff = now.diff(target, 'second');

  if (diff < 60) {
    return '刚刚';
  } else if (diff < 3600) {
    return `${Math.floor(diff / 60)}分钟前`;
  } else if (diff < 86400) {
    return `${Math.floor(diff / 3600)}小时前`;
  } else if (diff < 2592000) {
    return `${Math.floor(diff / 86400)}天前`;
  } else {
    return formatDate(dateTime);
  }
};

// 格式化手机号（中间4位隐藏）
export const formatPhone = (phone: string): string => {
  if (!phone || phone.length !== 11) {
    return phone;
  }
  return `${phone.slice(0, 3)}****${phone.slice(7)}`;
};
