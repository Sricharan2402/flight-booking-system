-- Create seats table
-- Individual seat inventory per flight with booking status

CREATE TABLE seats (
    seat_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_id UUID NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'RESERVED', 'BOOKED', 'BLOCKED')),
    booking_id UUID NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint to prevent duplicate seat numbers per flight
CREATE UNIQUE INDEX idx_seats_flight_seat_number ON seats(flight_id, seat_number);

-- Performance indexes for seat availability queries
CREATE INDEX idx_seats_flight_available ON seats(flight_id, status);
CREATE INDEX idx_seats_booking ON seats(booking_id) WHERE booking_id IS NOT NULL;

-- Create trigger for updated_at column
CREATE TRIGGER tr_seats_updated_at
    BEFORE UPDATE ON seats
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
