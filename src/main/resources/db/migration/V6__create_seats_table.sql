-- Create seats table
-- Individual seat inventory per flight with booking status

CREATE TABLE seats (
    seat_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_id UUID NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'RESERVED', 'BOOKED', 'BLOCKED')),
    booking_id UUID NULL,
    reserved_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Business logic constraints
    CONSTRAINT chk_booking_status_consistency CHECK (
        (status = 'BOOKED' AND booking_id IS NOT NULL) OR
        (status != 'BOOKED' AND (booking_id IS NULL OR status = 'RESERVED'))
    ),
    CONSTRAINT chk_reserved_until_logic CHECK (
        (status = 'RESERVED' AND reserved_until IS NOT NULL AND reserved_until > CURRENT_TIMESTAMP) OR
        (status != 'RESERVED' AND reserved_until IS NULL)
    )
);

-- Unique constraint to prevent duplicate seat numbers per flight
CREATE UNIQUE INDEX idx_seats_flight_seat_number ON seats(flight_id, seat_number);

-- Performance indexes for seat availability queries
CREATE INDEX idx_seats_flight_available ON seats(flight_id, status);
CREATE INDEX idx_seats_booking ON seats(booking_id) WHERE booking_id IS NOT NULL;

-- Index for cleanup of expired reservations
CREATE INDEX idx_seats_reserved_until ON seats(reserved_until) WHERE reserved_until IS NOT NULL;

-- Create trigger for updated_at column
CREATE TRIGGER tr_seats_updated_at
    BEFORE UPDATE ON seats
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add table and column comments
COMMENT ON TABLE seats IS 'Individual seat inventory per flight with real-time booking status';
COMMENT ON COLUMN seats.seat_number IS 'Seat identifier within aircraft (e.g., 12A, 15F)';
COMMENT ON COLUMN seats.status IS 'Current availability status of the seat';
COMMENT ON COLUMN seats.reserved_until IS 'Temporary reservation expiry time for seat locking';
COMMENT ON CONSTRAINT chk_booking_status_consistency ON seats IS 'Ensures booked seats have associated booking_id';
COMMENT ON CONSTRAINT chk_reserved_until_logic ON seats IS 'Ensures reserved seats have valid future expiry time';