USE `personal_saas`;

-- Retire the v0.3 personal-scope column only after the v0.4 owner column is
-- complete and all document readers/writers have been deployed.
DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_retire_team_preflight`$$
CREATE PROCEDURE `document_user_authorization_retire_team_preflight`()
BEGIN
    DECLARE v_owner_column_count INT DEFAULT 0;
    DECLARE v_owner_null_count INT DEFAULT 0;
    DECLARE v_owner_index_count INT DEFAULT 0;
    DECLARE v_team_column_count INT DEFAULT 0;
    DECLARE v_team_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_owner_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'owner_user_id';
    SELECT COUNT(*) INTO v_owner_null_count
    FROM `biz_document`
    WHERE `owner_user_id` IS NULL;
    SELECT COUNT(*) INTO v_owner_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_owner_deleted_time';
    SELECT COUNT(*) INTO v_team_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'team_id';
    SELECT COUNT(*) INTO v_team_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_scope_deleted_time';

    IF v_owner_column_count <> 1 OR v_owner_null_count <> 0
            OR v_owner_index_count = 0 OR v_team_column_count <> 1
            OR v_team_index_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document authorization team retirement preflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_retire_team_preflight`();
DROP PROCEDURE `document_user_authorization_retire_team_preflight`;

ALTER TABLE `biz_document`
    DROP INDEX `idx_document_scope_deleted_time`,
    DROP COLUMN `team_id`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_retire_team_postflight`$$
CREATE PROCEDURE `document_user_authorization_retire_team_postflight`()
BEGIN
    DECLARE v_owner_column_count INT DEFAULT 0;
    DECLARE v_owner_index_count INT DEFAULT 0;
    DECLARE v_team_column_count INT DEFAULT 0;
    DECLARE v_team_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_owner_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'owner_user_id';
    SELECT COUNT(*) INTO v_owner_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_owner_deleted_time';
    SELECT COUNT(*) INTO v_team_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'team_id';
    SELECT COUNT(*) INTO v_team_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_scope_deleted_time';

    IF v_owner_column_count <> 1 OR v_owner_index_count = 0
            OR v_team_column_count <> 0 OR v_team_index_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document authorization team retirement postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_retire_team_postflight`();
DROP PROCEDURE `document_user_authorization_retire_team_postflight`;
