ALTER TABLE bond ADD COLUMN requester_remark VARCHAR(30);
ALTER TABLE bond ADD COLUMN receiver_remark VARCHAR(30);

CREATE UNIQUE INDEX uk_bond_active_pair
    ON bond (LEAST(requester_id, receiver_id), GREATEST(requester_id, receiver_id))
    WHERE status = 'ACTIVE';
