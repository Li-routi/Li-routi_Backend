ALTER TABLE `group_chat_message`
    ADD KEY `idx_group_chat_message_group_id_created_at` (`group_id`, `created_at`);
