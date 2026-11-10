#!/usr/bin/env python3
"""发送本地 sandbox 已验签成功事实，不连接真实支付通道。"""
import argparse
import datetime
import hashlib
import hmac
import json
import os
import time
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=['payments', 'refunds'])
    parser.add_argument('number', help='paymentNo 或 refundNo')
    parser.add_argument('amount', help='实际金额，例如 12.30')
    parser.add_argument('--url', default='http://localhost:8080/api')
    parser.add_argument('--reference', help='供应商流水号；重试应保持相同流水')
    args = parser.parse_args()
    secret = os.environ.get('SANDBOX_CALLBACK_SECRET')
    if not secret:
        parser.error('请设置 SANDBOX_CALLBACK_SECRET')
    body = {
        'paymentNo' if args.kind == 'payments' else 'refundNo': args.number,
        'amount': args.amount,
        'currency': 'CNY',
        'providerReference': args.reference or f'sandbox-{args.number}',
        'completedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    }
    raw = json.dumps(body, separators=(',', ':')).encode()
    timestamp = str(int(time.time()))
    signature = hmac.new(secret.encode(), timestamp.encode() + b'.' + raw, hashlib.sha256).hexdigest()
    request = urllib.request.Request(
        f'{args.url.rstrip("/")}/payment-callbacks/sandbox/{args.kind}', data=raw,
        headers={'Content-Type': 'application/json', 'X-Sandbox-Timestamp': timestamp,
                 'X-Sandbox-Signature': signature}, method='POST')
    with urllib.request.urlopen(request, timeout=10) as response:
        print(response.read().decode())


if __name__ == '__main__':
    main()
