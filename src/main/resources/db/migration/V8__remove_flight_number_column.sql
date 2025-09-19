-- Remove flight_number column from flights table
-- flightId is sufficient for all internal operations and data relationships

-- Drop the index first
DROP INDEX IF EXISTS idx_flights_flight_number;

-- Remove the flight_number column
ALTER TABLE flights DROP COLUMN flight_number;

-- Update table comment to reflect the change
COMMENT ON TABLE flights IS 'Flight schedule information with pricing (flightNumber handled at API layer only)';