-- 같은 회원이 서로 다른 그룹에서 동일한 client_message_id를 사용할 수 있도록
-- 재전송 식별자의 유니크 범위를 그룹 단위로 제한한다.

-- 기존 유니크 인덱스는 sender_id 외래 키의 자식 인덱스 역할도 하므로,
-- 유니크 범위를 변경하기 전에 외래 키용 단독 인덱스를 추가한다.
ALTER TABLE `group_chat_message`
    ADD KEY `idx_group_chat_message_sender_id` (`sender_id`);

ALTER TABLE `group_chat_message`
    DROP INDEX `uk_group_chat_message_sender_client`,
    ADD UNIQUE KEY `uk_group_chat_message_sender_client`
        (`group_id`, `sender_id`, `client_message_id`);
