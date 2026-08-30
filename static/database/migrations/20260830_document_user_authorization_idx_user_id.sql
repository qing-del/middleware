USE `personal_saas`;

-- Add the explicitly named user lookup index while retaining the existing
-- visibility index used by document ACL queries.
DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_idx_user_id_preflight`$$
CREATE PROCEDURE `document_user_authorization_idx_user_id_preflight`()
BEGIN
    DECLARE v_table_count INT DEFAULT 0;
    DECLARE v_user_column_count INT DEFAULT 0;
    DECLARE v_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'biz_document_user';
    SELECT COUNT(*) INTO v_user_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'biz_document_user'
      AND column_name = 'user_id';
    SELECT COUNT(*) INTO v_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'biz_document_user'
      AND index_name = 'idx_user_id';

    IF v_table_count <> 1 OR v_user_column_count <> 1 OR v_index_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document user authorization user index preflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_idx_user_id_preflight`();
DROP PROCEDURE `document_user_authorization_idx_user_id_preflight`;

ALTER TABLE `biz_document_user`
    ADD KEY `idx_user_id` (`user_id`);

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_idx_user_id_postflight`$$
CREATE PROCEDURE `document_user_authorization_idx_user_id_postflight`()
BEGIN
    DECLARE v_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'biz_document_user'
      AND index_name = 'idx_user_id';

    IF v_index_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document user authorization user index postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_idx_user_id_postflight`();
DROP PROCEDURE `document_user_authorization_idx_user_id_postflight`;
