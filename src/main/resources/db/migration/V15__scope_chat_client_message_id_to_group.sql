-- 같은 회원이 서로 다른 그룹에서 동일한 client_message_id를 사용할 수 있도록
-- 재전송 식별자의 유니크 범위를 그룹 단위로 제한한다.

ALTER TABLE `group_chat_message`
    DROP INDEX `uk_group_chat_message_sender_client`,
    ADD UNIQUE KEY `uk_group_chat_message_sender_client`
        (`group_id`, `sender_id`, `client_message_id`);
