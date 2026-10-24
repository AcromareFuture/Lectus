import React from 'react';
import { MallOrders } from './MallPages';

export default function AccountOrderPanel({ member, compact = false }) {
  return <MallOrders member={member} compact={compact} />;
}
