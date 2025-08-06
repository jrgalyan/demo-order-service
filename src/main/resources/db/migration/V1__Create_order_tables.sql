-- Create orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL CHECK (total_amount >= 0),
    shipping_address JSONB NOT NULL,
    billing_address JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for orders table
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_updated_at ON orders(updated_at DESC);
CREATE INDEX idx_orders_total_amount ON orders(total_amount);
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_status_updated ON orders(status, updated_at DESC);

-- Create composite index for date range queries
CREATE INDEX idx_orders_created_range ON orders(created_at) WHERE status IN ('DELIVERED', 'SHIPPED');

-- Create partial indexes for active orders
CREATE INDEX idx_orders_active ON orders(status, created_at DESC) WHERE status NOT IN ('CANCELLED', 'RETURNED', 'REFUNDED');
CREATE INDEX idx_orders_pending ON orders(created_at ASC) WHERE status = 'PENDING';
CREATE INDEX idx_orders_processing ON orders(updated_at ASC) WHERE status = 'PROCESSING';

-- Create order_items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    total_price DECIMAL(10,2) NOT NULL CHECK (total_price >= 0)
);

-- Create indexes for order_items table
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_product_sku ON order_items(product_sku);
CREATE INDEX idx_order_items_quantity ON order_items(quantity);
CREATE INDEX idx_order_items_unit_price ON order_items(unit_price);
CREATE INDEX idx_order_items_total_price ON order_items(total_price);

-- Create composite indexes for analytics
CREATE INDEX idx_order_items_product_order ON order_items(product_id, order_id);
CREATE INDEX idx_order_items_sku_quantity ON order_items(product_sku, quantity);

-- Add constraint to ensure order status is valid
ALTER TABLE orders ADD CONSTRAINT chk_orders_status 
    CHECK (status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED', 'REFUNDED'));

-- Add constraint to ensure total_price equals unit_price * quantity
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_total_price 
    CHECK (total_price = unit_price * quantity);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at for orders
CREATE TRIGGER update_orders_updated_at 
    BEFORE UPDATE ON orders 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create function to validate order total matches sum of item totals
CREATE OR REPLACE FUNCTION validate_order_total()
RETURNS TRIGGER AS $$
DECLARE
    calculated_total DECIMAL(10,2);
BEGIN
    -- Calculate total from order items
    SELECT COALESCE(SUM(total_price), 0) INTO calculated_total
    FROM order_items 
    WHERE order_id = COALESCE(NEW.order_id, OLD.order_id);
    
    -- Update order total if it doesn't match
    UPDATE orders 
    SET total_amount = calculated_total
    WHERE id = COALESCE(NEW.order_id, OLD.order_id)
    AND total_amount != calculated_total;
    
    RETURN COALESCE(NEW, OLD);
END;
$$ language 'plpgsql';

-- Create triggers to maintain order total consistency
CREATE TRIGGER validate_order_total_on_insert
    AFTER INSERT ON order_items
    FOR EACH ROW
    EXECUTE FUNCTION validate_order_total();

CREATE TRIGGER validate_order_total_on_update
    AFTER UPDATE ON order_items
    FOR EACH ROW
    EXECUTE FUNCTION validate_order_total();

CREATE TRIGGER validate_order_total_on_delete
    AFTER DELETE ON order_items
    FOR EACH ROW
    EXECUTE FUNCTION validate_order_total();

-- Create function to prevent modification of completed orders
CREATE OR REPLACE FUNCTION prevent_completed_order_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('DELIVERED', 'CANCELLED', 'RETURNED', 'REFUNDED') THEN
        RAISE EXCEPTION 'Cannot modify order in final status: %', OLD.status;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to prevent modification of completed orders
CREATE TRIGGER prevent_completed_order_modification_trigger
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION prevent_completed_order_modification();

-- Create view for order summary with item counts
CREATE VIEW order_summary AS
SELECT 
    o.id,
    o.order_number,
    o.user_id,
    o.status,
    o.total_amount,
    o.created_at,
    o.updated_at,
    COUNT(oi.id) as item_count,
    SUM(oi.quantity) as total_quantity,
    AVG(oi.unit_price) as avg_item_price,
    MAX(oi.unit_price) as max_item_price,
    MIN(oi.unit_price) as min_item_price
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number, o.user_id, o.status, o.total_amount, o.created_at, o.updated_at;

-- Create view for sales analytics
CREATE VIEW sales_analytics AS
SELECT 
    o.status,
    DATE_TRUNC('day', o.created_at) as order_date,
    COUNT(o.id) as order_count,
    SUM(o.total_amount) as total_revenue,
    AVG(o.total_amount) as avg_order_value,
    SUM(oi.quantity) as total_items_sold,
    COUNT(DISTINCT o.user_id) as unique_customers
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.status IN ('DELIVERED', 'SHIPPED')
GROUP BY o.status, DATE_TRUNC('day', o.created_at);

-- Create view for product sales performance
CREATE VIEW product_sales_performance AS
SELECT 
    oi.product_id,
    oi.product_name,
    oi.product_sku,
    COUNT(DISTINCT oi.order_id) as order_count,
    SUM(oi.quantity) as total_quantity_sold,
    SUM(oi.total_price) as total_revenue,
    AVG(oi.unit_price) as avg_selling_price,
    MAX(oi.unit_price) as max_selling_price,
    MIN(oi.unit_price) as min_selling_price
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.status IN ('DELIVERED', 'SHIPPED')
GROUP BY oi.product_id, oi.product_name, oi.product_sku;

-- Create view for customer order history
CREATE VIEW customer_order_history AS
SELECT 
    o.user_id,
    COUNT(o.id) as total_orders,
    SUM(o.total_amount) as total_spent,
    AVG(o.total_amount) as avg_order_value,
    MAX(o.total_amount) as max_order_value,
    MIN(o.created_at) as first_order_date,
    MAX(o.created_at) as last_order_date,
    COUNT(CASE WHEN o.status = 'DELIVERED' THEN 1 END) as completed_orders,
    COUNT(CASE WHEN o.status = 'CANCELLED' THEN 1 END) as cancelled_orders
FROM orders o
GROUP BY o.user_id;

-- Create view for order status transitions (for future status history tracking)
CREATE VIEW order_status_summary AS
SELECT 
    status,
    COUNT(*) as order_count,
    SUM(total_amount) as total_value,
    AVG(total_amount) as avg_value,
    MIN(created_at) as earliest_order,
    MAX(created_at) as latest_order
FROM orders
GROUP BY status;

-- Insert sample data for testing (optional)
-- Sample orders
INSERT INTO orders (order_number, user_id, status, total_amount, shipping_address, billing_address) VALUES 
('ORD-1234567890-0001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DELIVERED', 299.98, 
 '{"addressLine1": "123 Main St", "city": "New York", "state": "NY", "postalCode": "10001", "country": "USA"}',
 '{"addressLine1": "123 Main St", "city": "New York", "state": "NY", "postalCode": "10001", "country": "USA"}'),
('ORD-1234567890-0002', 'b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22', 'PROCESSING', 149.99,
 '{"addressLine1": "456 Oak Ave", "city": "Los Angeles", "state": "CA", "postalCode": "90210", "country": "USA"}',
 '{"addressLine1": "456 Oak Ave", "city": "Los Angeles", "state": "CA", "postalCode": "90210", "country": "USA"}'),
('ORD-1234567890-0003', 'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'PENDING', 89.99,
 '{"addressLine1": "789 Pine Rd", "city": "Chicago", "state": "IL", "postalCode": "60601", "country": "USA"}',
 '{"addressLine1": "789 Pine Rd", "city": "Chicago", "state": "IL", "postalCode": "60601", "country": "USA"}');

-- Sample order items
INSERT INTO order_items (order_id, product_id, product_name, product_sku, quantity, unit_price, total_price) VALUES 
((SELECT id FROM orders WHERE order_number = 'ORD-1234567890-0001'), 'd3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 'Wireless Headphones', 'WH-001', 2, 149.99, 299.98),
((SELECT id FROM orders WHERE order_number = 'ORD-1234567890-0002'), 'e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 'Bluetooth Speaker', 'BS-002', 1, 149.99, 149.99),
((SELECT id FROM orders WHERE order_number = 'ORD-1234567890-0003'), 'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 'Phone Case', 'PC-003', 1, 89.99, 89.99);

-- Create indexes on JSONB columns for address queries
CREATE INDEX idx_orders_shipping_address_gin ON orders USING gin(shipping_address);
CREATE INDEX idx_orders_billing_address_gin ON orders USING gin(billing_address);

-- Create expression indexes for common JSONB queries
CREATE INDEX idx_orders_shipping_country ON orders((shipping_address->>'country'));
CREATE INDEX idx_orders_shipping_state ON orders((shipping_address->>'state'));
CREATE INDEX idx_orders_billing_country ON orders((billing_address->>'country'));

-- Add comments for documentation
COMMENT ON TABLE orders IS 'Main orders table storing customer order information';
COMMENT ON TABLE order_items IS 'Order line items with product details and pricing';
COMMENT ON COLUMN orders.order_number IS 'Unique human-readable order identifier';
COMMENT ON COLUMN orders.status IS 'Current order status following defined workflow';
COMMENT ON COLUMN orders.shipping_address IS 'Shipping address stored as JSONB for flexibility';
COMMENT ON COLUMN orders.billing_address IS 'Billing address stored as JSONB for flexibility';
COMMENT ON COLUMN order_items.total_price IS 'Calculated as unit_price * quantity, enforced by constraint';

-- Create function for order number generation (if needed by application)
CREATE OR REPLACE FUNCTION generate_order_number()
RETURNS TEXT AS $$
DECLARE
    new_number TEXT;
    counter INTEGER := 1;
BEGIN
    LOOP
        new_number := 'ORD-' || EXTRACT(EPOCH FROM NOW())::BIGINT || '-' || LPAD(counter::TEXT, 4, '0');
        
        -- Check if this number already exists
        IF NOT EXISTS (SELECT 1 FROM orders WHERE order_number = new_number) THEN
            RETURN new_number;
        END IF;
        
        counter := counter + 1;
        
        -- Prevent infinite loop
        IF counter > 9999 THEN
            RAISE EXCEPTION 'Unable to generate unique order number';
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;