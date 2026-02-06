-- Tables are created in the 'shop' database (set via POSTGRES_DB in docker-compose)

CREATE TABLE IF NOT EXISTS users
(
    id       UUID PRIMARY KEY,
    name     VARCHAR UNIQUE NOT NULL,
    password VARCHAR        NOT NULL
);

CREATE TABLE IF NOT EXISTS brands
(
    id   UUID PRIMARY KEY,
    name VARCHAR UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS categories
(
    id   UUID PRIMARY KEY,
    name VARCHAR UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS items
(
    id          UUID PRIMARY KEY,
    name        VARCHAR UNIQUE NOT NULL,
    description VARCHAR        NOT NULL,
    price       NUMERIC        NOT NULL,
    brand_id    UUID           NOT NULL,
    category_id UUID           NOT NULL,
    CONSTRAINT brand_id_fkey FOREIGN KEY (brand_id)
        REFERENCES brands (id) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT category_id_fkey FOREIGN KEY (category_id)
        REFERENCES categories (id) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS orders
(
    id          UUID PRIMARY KEY,
    status      VARCHAR     NOT NULL,
    user_id     UUID        NOT NULL,
    payment_id  UUID UNIQUE,
    items       JSONB       NOT NULL,
    total_price NUMERIC     NOT NULL,
    CONSTRAINT user_id_fkey FOREIGN KEY (user_id)
        REFERENCES users (id) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- Sample data for testing
INSERT INTO brands (id, name) VALUES 
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Nike'),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'Adidas'),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'Puma')
ON CONFLICT DO NOTHING;

INSERT INTO categories (id, name) VALUES 
    ('d4e5f6a7-b8c9-0123-defa-234567890123', 'Clothing'),
    ('e5f6a7b8-c9d0-1234-efab-345678901234', 'Footwear'),
    ('f6a7b8c9-d0e1-2345-fabc-456789012345', 'Accessories')
ON CONFLICT DO NOTHING;

INSERT INTO items (id, name, description, price, brand_id, category_id) VALUES 
    ('11111111-1111-1111-1111-111111111111', 'Running Shirt', 'Lightweight breathable running shirt', 29.99, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'd4e5f6a7-b8c9-0123-defa-234567890123'),
    ('22222222-2222-2222-2222-222222222222', 'Classic T-Shirt', 'Cotton casual t-shirt', 19.99, 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 'd4e5f6a7-b8c9-0123-defa-234567890123'),
    ('33333333-3333-3333-3333-333333333333', 'Sport Shorts', 'Quick-dry athletic shorts', 34.99, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'd4e5f6a7-b8c9-0123-defa-234567890123'),
    ('44444444-4444-4444-4444-444444444444', 'Running Shoes', 'Cushioned running shoes', 129.99, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'e5f6a7b8-c9d0-1234-efab-345678901234'),
    ('55555555-5555-5555-5555-555555555555', 'Training Sneakers', 'Versatile training sneakers', 89.99, 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 'e5f6a7b8-c9d0-1234-efab-345678901234'),
    ('66666666-6666-6666-6666-666666666666', 'Casual Sneakers', 'Everyday casual sneakers', 79.99, 'c3d4e5f6-a7b8-9012-cdef-123456789012', 'e5f6a7b8-c9d0-1234-efab-345678901234'),
    ('77777777-7777-7777-7777-777777777777', 'Sports Cap', 'Adjustable sports cap', 24.99, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'f6a7b8c9-d0e1-2345-fabc-456789012345'),
    ('88888888-8888-8888-8888-888888888888', 'Gym Bag', 'Large capacity gym bag', 49.99, 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 'f6a7b8c9-d0e1-2345-fabc-456789012345'),
    ('99999999-9999-9999-9999-999999999999', 'Winter Jacket', 'Insulated winter jacket', 149.99, 'c3d4e5f6-a7b8-9012-cdef-123456789012', 'd4e5f6a7-b8c9-0123-defa-234567890123'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Polo Shirt', 'Classic polo shirt', 39.99, 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 'd4e5f6a7-b8c9-0123-defa-234567890123')
ON CONFLICT DO NOTHING;

