-- Create journeys table
-- Precomputed travel combinations (1-3 flights) for fast search

CREATE TABLE journeys (
    journey_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_details JSONB NOT NULL,
    source_airport CHAR(3) NOT NULL,
    destination_airport CHAR(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    total_price DECIMAL(10,2) NOT NULL CHECK (total_price >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Business logic constraints
    CONSTRAINT chk_different_airports CHECK (source_airport != destination_airport),
    CONSTRAINT chk_departure_before_arrival CHECK (departure_time < arrival_time)
);

-- Performance indexes for search queries
CREATE INDEX idx_journeys_source_dest ON journeys(source_airport, destination_airport);
CREATE INDEX idx_journeys_price ON journeys(total_price);
CREATE INDEX idx_journeys_departure_time ON journeys(departure_time);
CREATE INDEX idx_journeys_arrival_time ON journeys(arrival_time);
CREATE INDEX idx_journeys_status ON journeys(status);

-- Unique constraint to prevent duplicate journey combinations
-- Using expression index on normalized flight_details for deduplication
CREATE UNIQUE INDEX idx_journeys_flight_combination
ON journeys USING btree(
    (flight_details->'flights')
) WHERE status = 'ACTIVE';

-- GIN index for JSONB queries on flight_details
CREATE INDEX idx_journeys_flight_details ON journeys USING gin(flight_details);

-- Create trigger for updated_at column
CREATE TRIGGER tr_journeys_updated_at
    BEFORE UPDATE ON journeys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();