-- reread marker는 인증글에만 종속된다. 인증의 개별 삭제와 그룹 hard delete cascade 경로에서
-- marker를 같은 트랜잭션으로 제거해 orphan을 남기지 않는다.
ALTER TABLE `group_routine_verification_reread`
    ADD CONSTRAINT `FK_group_routine_verification_reread_verification`
        FOREIGN KEY (`verification_id`) REFERENCES `group_routine_verification` (`id`)
        ON DELETE CASCADE;
