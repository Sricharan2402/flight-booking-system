-- Create reusable function for updating updated_at timestamp
-- This function will be used by triggers on all tables

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Add comment for documentation
COMMENT ON FUNCTION update_updated_at_column() IS 'Trigger function to automatically update updated_at column on row modification';