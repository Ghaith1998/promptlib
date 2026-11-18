-- Seed the global category taxonomy.
-- Stable UUIDs so tests, exports and authoring tools can reference them.
-- Idempotent: safe to run against a database that already has the rows.

INSERT IGNORE INTO category (id, slug, name, description, color, sort_order, active, created_at, updated_at) VALUES
  (UNHEX('0193A0000000700080000000000001'), 'coding',       'Coding',       'Code review, generation, refactoring and debugging prompts', '#4F46E5', 10, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000002'), 'writing',      'Writing',      'Drafting, editing, tone and style prompts',                  '#0EA5E9', 20, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000003'), 'marketing',    'Marketing',    'Campaigns, positioning, copy and social prompts',            '#F97316', 30, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000004'), 'education',    'Education',    'Explaining, tutoring, quizzing and study prompts',           '#22C55E', 40, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000005'), 'research',     'Research',     'Summarisation, extraction and analysis prompts',             '#8B5CF6', 50, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000006'), 'productivity', 'Productivity', 'Planning, checklists and workflow prompts',                  '#EAB308', 60, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (UNHEX('0193A0000000700080000000000007'), 'other',        'Other',        'Prompts that do not fit the categories above',               NULL,     99, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
