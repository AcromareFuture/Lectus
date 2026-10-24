import React from 'react';
import { LoaderCircle, PackageCheck } from 'lucide-react';
import './trade-feedback.css';

export function OrderSubmitting({ title = '正在生成订单', copy = '正在核对商品价格、库存和收货信息，请稍候。' }) {
  return <div className="trade-submitting" role="status" aria-live="polite" aria-busy="true">
    <div className="trade-submitting-content"><span className="trade-process-icon"><PackageCheck size={30} /><LoaderCircle className="trade-spinner" size={64} /></span><strong>{title}</strong><p>{copy}</p><span className="trade-processing-line"><i /><i /><i /></span></div>
  </div>;
}
export function PaymentResult({ compact = false, label = '支付完成' }) {
  return <span className={`trade-success-icon ${compact ? 'compact' : ''}`} role="img" aria-label={label}>
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true"><circle className="trade-success-circle" cx="32" cy="32" r="28" /><path className="trade-success-check" d="M19 33l9 9 18-20" /></svg>
  </span>;
}
