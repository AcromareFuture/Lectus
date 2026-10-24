import test from 'node:test';
import assert from 'node:assert/strict';
import { filterAccountOrders } from './account-orders.js';

test('order status filters retain every record exactly once across active and terminal states', () => {
  const orders = ['PENDING', 'PAID', 'CANCELLED', 'EXPIRED'].map((state, id) => ({ id, state }));
  assert.deepEqual(filterAccountOrders(orders, 'all'), orders);
  assert.deepEqual(filterAccountOrders(orders, 'pending').map(order => order.id), [0]);
  assert.deepEqual(filterAccountOrders(orders, 'completed').map(order => order.id), [1]);
  assert.deepEqual(filterAccountOrders(orders, 'closed').map(order => order.id), [2, 3]);
  assert.equal(new Set(['pending', 'completed', 'closed'].flatMap(group => filterAccountOrders(orders, group)).map(order => order.id)).size, orders.length);
  assert.deepEqual(filterAccountOrders([], 'completed'), []);
});
