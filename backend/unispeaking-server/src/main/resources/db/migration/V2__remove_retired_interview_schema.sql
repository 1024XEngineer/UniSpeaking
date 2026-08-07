-- Remove the retired interview scene without rewriting the applied V1 baseline.
-- Related session rows have no database foreign keys, so they are cleaned in
-- dependency order before the scene type constraint is tightened.

DELETE FROM turn_evaluation
WHERE session_id IN (
    SELECT session_id
    FROM practice_session
    WHERE scene_type = 'INTERVIEW_SCENE'
);

DELETE FROM session_evaluation
WHERE session_id IN (
    SELECT session_id
    FROM practice_session
    WHERE scene_type = 'INTERVIEW_SCENE'
);

DELETE FROM session_message
WHERE session_id IN (
    SELECT session_id
    FROM practice_session
    WHERE scene_type = 'INTERVIEW_SCENE'
);

DELETE FROM practice_session
WHERE scene_type = 'INTERVIEW_SCENE';

ALTER TABLE practice_session
DROP CONSTRAINT IF EXISTS practice_session_scene_type_check;

ALTER TABLE practice_session
ADD CONSTRAINT practice_session_scene_type_check
CHECK (scene_type IN (
    'FREE_CHAT',
    'CUSTOM_SCENE',
    'IELTS_SCENE'
));

DROP TABLE IF EXISTS interview_report;
DROP TABLE IF EXISTS interview_question;
DROP TABLE IF EXISTS interview;
