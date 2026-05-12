-- V2__add_views.sql
-- H2 (dev) + PostgreSQL (prod) compatible

DROP VIEW IF EXISTS transaction_summary;

CREATE VIEW transaction_summary AS
SELECT
    CAST(settled_at AS DATE)                                              AS settlement_date,
    COUNT(*)                                                              AS total_count,
    SUM(CASE WHEN status = 'SETTLED'  THEN 1 ELSE 0 END)                 AS settled_count,
    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END)                 AS rejected_count,
    COALESCE(SUM(CASE WHEN status = 'SETTLED' THEN amount ELSE 0 END), 0) AS total_volume,
    AVG(CASE WHEN status = 'SETTLED' THEN CAST(hop_count AS DOUBLE) END)  AS avg_hops
FROM transactions
GROUP BY CAST(settled_at AS DATE);
