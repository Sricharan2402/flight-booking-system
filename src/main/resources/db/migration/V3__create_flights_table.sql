-- Create flights table
-- Core flight schedule information

CREATE TABLE flights (
    flight_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_number VARCHAR(10) NOT NULL,
    source_airport CHAR(3) NOT NULL,
    destination_airport CHAR(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    airplane_id UUID NOT NULL,
    total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED', 'DELAYED', 'COMPLETED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Business logic constraints
    CONSTRAINT chk_departure_before_arrival CHECK (departure_time < arrival_time),
    CONSTRAINT chk_different_airports CHECK (source_airport != destination_airport)
);

-- Performance indexes for search queries
CREATE INDEX idx_flights_source_dest_time ON flights(source_airport, destination_airport, departure_time);
CREATE INDEX idx_flights_departure_time ON flights(departure_time);
CREATE INDEX idx_flights_destination_time ON flights(destination_airport, departure_time);
CREATE INDEX idx_flights_status ON flights(status);
CREATE INDEX idx_flights_airplane ON flights(airplane_id);

-- Index for flight number lookups
CREATE INDEX idx_flights_flight_number ON flights(flight_number);

-- Create trigger for updated_at column
CREATE TRIGGER tr_flights_updated_at
    BEFORE UPDATE ON flights
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
