-- Create bookings table
-- Customer reservations for complete journeys

CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    journey_id UUID NOT NULL,
    number_of_seats INTEGER NOT NULL CHECK (number_of_seats > 0 AND number_of_seats <= 10),
    booking_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('RESERVED', 'CONFIRMED', 'CANCELLED', 'REFUNDED')),
    payment_id VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Performance indexes for common queries
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_journey ON bookings(journey_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_booking_time ON bookings(booking_time);

-- Index for payment tracking
CREATE INDEX idx_bookings_payment ON bookings(payment_id) WHERE payment_id IS NOT NULL;

-- Create trigger for updated_at column
CREATE TRIGGER tr_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add table and column comments
COMMENT ON TABLE bookings IS 'Customer reservations for complete journey combinations';
COMMENT ON COLUMN bookings.user_id IS 'Reference to customer making the booking';
COMMENT ON COLUMN bookings.number_of_seats IS 'Number of passenger seats reserved for this booking';
COMMENT ON COLUMN bookings.booking_time IS 'When the initial reservation was made';
COMMENT ON COLUMN bookings.payment_id IS 'External payment system reference for confirmed bookings';