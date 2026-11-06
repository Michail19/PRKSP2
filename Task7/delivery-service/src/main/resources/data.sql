INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (1, 1, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 4039928, TIMESTAMP '2024-06-03 10:10:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (2, 2, 2, 'CANCELLED', 'проспект Мира, дом 25', 2589926, TIMESTAMP '2024-06-03 12:31:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (3, 3, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 4619790, TIMESTAMP '2024-06-03 15:28:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (4, 4, 5, 'COMPLETED', 'шоссе Лесное, дом 30', 1179941, TIMESTAMP '2024-06-03 17:16:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (5, 5, 2, 'CANCELLED', 'проспект Мира, дом 25', 649974, TIMESTAMP '2024-06-04 09:23:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (6, 6, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 2789887, TIMESTAMP '2024-06-04 11:52:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (7, 7, 5, 'COMPLETED', 'шоссе Лесное, дом 30', 919977, TIMESTAMP '2024-06-04 13:47:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (8, 8, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 5949824, TIMESTAMP '2024-06-04 14:34:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (9, 9, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 824945, TIMESTAMP '2024-06-04 16:10:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (10, 10, 1, 'COMPLETED', 'улица Солнечная, дом 10', 3119892, TIMESTAMP '2024-06-06 10:05:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (11, 11, 4, 'COMPLETED', 'бульвар Зелёный, дом 20', 1364909, TIMESTAMP '2024-06-06 11:30:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (12, 12, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 4719883, TIMESTAMP '2024-06-06 15:24:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (13, 13, 3, 'COMPLETED', 'переулок Цветочный, дом 15', 1364961, TIMESTAMP '2024-06-07 09:49:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (14, 14, 3, 'DELIVERY', 'переулок Цветочный, дом 15', 649974, TIMESTAMP '2024-06-07 11:57:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (15, 15, 4, 'ASSEMBLY', 'бульвар Зелёный, дом 20', 1479926, TIMESTAMP '2024-06-07 12:58:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (16, 16, 3, 'DELIVERY', 'переулок Цветочный, дом 15', 2124921, TIMESTAMP '2024-06-07 13:05:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (17, 17, 5, 'COMPLETED', 'шоссе Лесное, дом 30', 1249950, TIMESTAMP '2024-06-07 13:14:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (18, 18, 3, 'ASSEMBLY', 'переулок Цветочный, дом 15', 4559895, TIMESTAMP '2024-06-07 13:20:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (19, 19, 4, 'SUBMITTED', 'бульвар Зелёный, дом 20', 2639956, TIMESTAMP '2024-06-07 13:45:00');
INSERT INTO deliveries (id, order_id, darkstore_id, status, address, price_kopecks, updated_at) VALUES (20, 20, 2, 'CANCELLED', 'проспект Мира, дом 25', 1259916, TIMESTAMP '2024-06-07 13:48:00');

-- Explicit IDs in seed data do not necessarily advance the H2 identity counter.
ALTER TABLE deliveries ALTER COLUMN id RESTART WITH 21;
