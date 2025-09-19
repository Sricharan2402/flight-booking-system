-- Create airplanes table
-- Base table for aircraft information

CREATE TABLE airplanes (
    airplane_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tail_number VARCHAR(20) UNIQUE NOT NULL,
    model VARCHAR(50) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for status queries
CREATE INDEX idx_airplanes_status ON airplanes(status);

-- Create index for tail number lookups
CREATE INDEX idx_airplanes_tail_number ON airplanes(tail_number);

-- Create trigger for updated_at column
CREATE TRIGGER tr_airplanes_updated_at
    BEFORE UPDATE ON airplanes
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
