BEGIN;

ALTER TABLE "user"
ADD COLUMN IF NOT EXISTS avatar_object_key VARCHAR(512);

ALTER TABLE "user"
DROP CONSTRAINT IF EXISTS user_avatar_object_key_check;

ALTER TABLE "user"
ADD CONSTRAINT user_avatar_object_key_check
CHECK (avatar_object_key IS NULL OR BTRIM(avatar_object_key) <> '');

COMMENT ON COLUMN "user".avatar_object_key IS
'用户头像在对象存储中的对象 Key；不保存签名 URL、Bucket 密钥或完整访问地址';

COMMIT;
