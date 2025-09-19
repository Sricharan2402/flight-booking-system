-- Remove capacity/seat information from flights and airplanes tables
-- Seat information should be tracked exclusively in the seats table

-- Remove total_capacity column from flights table
-- This will be calculated by counting seats in the seats table
ALTER TABLE flights DROP COLUMN total_capacity;

-- Remove capacity column from airplanes table
-- Seat configuration will be determined by the seats table entries
ALTER TABLE airplanes DROP COLUMN capacity;

-- Add comments to clarify the change
COMMENT ON TABLE flights IS 'Flight schedule information with pricing (capacity tracked in seats table)';
COMMENT ON TABLE airplanes IS 'Aircraft information including tail numbers (capacity tracked in seats table)';