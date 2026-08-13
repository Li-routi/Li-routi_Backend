ALTER TABLE `group_chat_message`
    ADD COLUMN `reply_to_message_id` bigint DEFAULT NULL AFTER `emoticon_id`,
    ADD KEY `idx_group_chat_message_reply_to_message_id` (`reply_to_message_id`),
    ADD CONSTRAINT `FK_group_chat_message_reply_to`
        FOREIGN KEY (`reply_to_message_id`) REFERENCES `group_chat_message` (`id`);
