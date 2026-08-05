-- IELTS user settings, generated practice data, topic normalization and
-- evaluation tables are part of the same initial IELTS migration.
-- Replace imported question sentences with concise searchable topic titles.
UPDATE ielts_topic AS topic
SET title = normalized.title,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('ielts_group_77f5e1acf5794d2745851ec5', 'Weekends'),
    ('ielts_group_68f7ea2a1936fad32064fca4', 'Music'),
    ('ielts_group_2f06a06285774f1c0753f32b', 'Travel'),
    ('ielts_group_6a0ee143437af4dfe3b683de', 'Secondary School'),
    ('ielts_group_0238d10e334b7fc92e420460', 'Food'),
    ('ielts_group_114210b402b42fb688136c68', 'Friends'),
    ('ielts_group_80475e6f6c11c8ae9262ea53', 'Photography'),
    ('ielts_group_fbbda32a8f9b929096b71867', 'Names'),
    ('ielts_group_da3f34e24310316b74551888', 'Healthy Eating'),
    ('ielts_group_316b768c2971d4afea3494b4', 'Singing'),
    ('ielts_group_1ab7feaaca382654dbbe5d0c', 'Clothes'),
    ('ielts_group_faa47c79d8b5b97ac6c179e8', 'Art Lessons'),
    ('ielts_group_e6a22eba88ea99c2488f5d6e', 'Television Programmes'),
    ('ielts_group_6718e31de17a00bfba31fd56', 'Television Programmes'),
    ('ielts_group_dcb4a2ff3d7efcbfbb91a072', 'Age'),
    ('ielts_group_928177c0ed22b9e7930d4b08', 'Payment Methods'),
    ('ielts_group_4d22e75784312121bbff9673', 'Animals and Birds'),
    ('ielts_group_24832b1a895e662fdf87fa62', 'Future Jobs'),
    ('ielts_group_e792b64441a899bc245fd1e3', 'Social Media'),
    ('ielts_group_b2e2ad85299d0221ea8bd4ed', 'Neighbours'),
    ('ielts_group_176d11e356a9734b4346d069', 'Neighbourhood'),
    ('ielts_group_77a46ab7af5d623a5eecc380', 'Work and Study Emails'),
    ('ielts_group_ab5eb837824a1b14cd351de9', 'Languages'),
    ('ielts_group_c79542b3407bbb05387ceb71', 'Swimming'),
    ('ielts_group_0289418712a43b1f2f6e8ced', 'Jewellery'),
    ('ielts_group_fbe3db3c0008a1681cdd49df', 'Study and Work Companions'),
    ('ielts_group_207db5273c1911f79750d56e', 'Flowers and Plants'),
    ('ielts_group_7feb5a01b864188661c471fb', 'Summer'),
    ('ielts_group_68ddfbdf20a28980e0ec9037', 'Fast Food'),
    ('ielts_group_6309c60c2c981dfc71553fb3', 'History Lessons'),
    ('ielts_group_28edbea726ac639756bd5b1a', 'Childhood Books'),
    ('ielts_group_099538c1c5e6481d6312cf53', 'Drinks with Dinner'),
    ('ielts_group_9674ce2055dd050d3ce57d81', 'Maps'),
    ('ielts_group_abedc1ec6288c04ae34d78cf', 'Household Bills'),
    ('ielts_group_c519422db00c2300207cfe48', 'Science'),
    ('ielts_group_ed9e5e66931cfb7c9211d5b2', 'Online Shopping'),
    ('ielts_group_cb613aea06b5ce17b8c894dc', 'Sleep'),
    ('ielts_group_29202ba9f987d6c4ab97215b', 'International Food'),
    ('ielts_group_de52fe85145ede9c154e1fbe', 'Air Travel'),
    ('ielts_group_dae65a678d05b232a5ecc9a5', 'Holidays'),
    ('ielts_group_05a1753c2dc851356c08c679', 'Cafes'),
    ('ielts_group_19667131ad766ba4769dff54', 'Walking'),
    ('ielts_group_7a880c9981b18bb3226d3eaa', 'Fruit'),
    ('ielts_group_4c7570cefaaee4de3fa529b3', 'Museums'),
    ('ielts_group_30dd1453a158f896cd3ac708', 'Personal Qualities'),
    ('ielts_group_83a3e38171e470100168370a', 'Your Country'),
    ('ielts_group_142c209f98f0afbe862e70a3', 'Colours'),
    ('ielts_group_36d2813a131477f61f7b6fd9', 'Evening Relaxation'),
    ('ielts_group_7541522dc2f531d9b1a837b4', 'Clothes and Fashion'),
    ('ielts_group_7e039fae864af0d84279fc77', 'Dancing'),
    ('ielts_group_119b66c935ff9a76920b9393', 'Musical Instruments'),
    ('ielts_group_d25eefd3ba32c335dc627e9c', 'Commuting'),
    ('ielts_group_231b1ea0eff1f925ffd2ee08', 'Friendships'),
    ('ielts_group_84467184f8e30142e2ba9b72', 'Staying in Touch'),
    ('ielts_group_fd87f9e8244663575b2c6990', 'Laughter'),
    ('ielts_group_4553756e69e805f9168cc6ef', 'Cold Weather'),
    ('ielts_group_1ff13574127bc8c89b6fd1b2', 'Travel to Work or College'),
    ('ielts_group_8b6320adfe6d211e9465affb', 'Neighbours Next Door'),
    ('ielts_group_b185e26c3c5e9973c7aa5abe', 'Magazines and Newspapers'),
    ('ielts_group_41f1302c7af473958943a541', 'Flowers at Home'),
    ('ielts_group_aebd012c55d1f2ab2284180a', 'Television'),
    ('ielts_group_48b131afd9427f5dda2dbde5', 'Games'),
    ('ielts_group_fe8c31c1dd295bc6f4d555a7', 'Gifts'),
    ('ielts_group_8acd2e4393cc50fb34169fc9', 'Telephone Calls'),
    ('ielts_group_cd2f7f56c24afb9f8628847e', 'Bicycles'),
    ('ielts_group_fc280cf842396bee1e3f02f5', '擅长的人与技能'),
    ('ielts_group_6f049c05be622ee6b9fb8e81', '家附近的商店'),
    ('ielts_group_daf5de8eb77a7f3a79309007', '熟悉的孩子'),
    ('ielts_group_723dbc63f8854d127611385d', '未来想拥有的物品'),
    ('ielts_group_857c5988d17a79cb8203cae7', '熟人的住所'),
    ('ielts_group_f3736b67d6873886938f6bce', '想见的作家'),
    ('ielts_group_365a7431bc6cfb95f6c4909b', '完美天气的一天'),
    ('ielts_group_7b6194c023847b90b752594b', '有趣的电视纪录片'),
    ('ielts_group_93c0d6df6b5cc880f3e36dae', '长时间等待'),
    ('ielts_group_809bf08571b17ccf3ff1de65', '本国知名演员'),
    ('ielts_group_23c192703c78af237cf6a6ad', '关于花钱的讨论'),
    ('ielts_group_d42ba5227e4e2d714232a3ea', '参观亲友的工作场所'),
    ('ielts_group_a5b73a4b3bcd8ae7103e4f89', '创业的人'),
    ('ielts_group_d71418ecdecbb9c0966788ea', '初次使用新科技设备'),
    ('ielts_group_e4ab6b6b5505800a7f38a38d', '工作或学习中的讨论'),
    ('ielts_group_aca0b0ab827f8b299a7334ec', '有帮助的网站'),
    ('ielts_group_b03fd96ecce57ff15f430376', '引人思考的书'),
    ('ielts_group_7e4b0bd283334e1f53c1f5dc', '为家里购买的物品'),
    ('ielts_group_39318972febb6a4d5b2b32e4', '成功完成的困难任务'),
    ('ielts_group_eae3f498fdb31cbfc98e73a7', '购物网站'),
    ('ielts_group_4256df9ea5c9efcd5a534bd1', '熟悉的酒店'),
    ('ielts_group_628141a2c1d7d676a1d49399', '购物网站'),
    ('ielts_group_4f3e200c5df43d4b36070841', '知名企业家'),
    ('ielts_group_a1a56ff2c48862b2ff449beb', '科学类电视节目'),
    ('ielts_group_b984cf767b0f92d660ca99b4', '喜欢的旅游景点'),
    ('ielts_group_8490468e5b80d07f5e54a94b', '产品或服务评论'),
    ('ielts_group_151e897b2777e298677d7518', '想拥有的奢侈品'),
    ('ielts_group_35e27b944effd9e030de1524', '停止使用的科技产品'),
    ('ielts_group_14dba0d72578c0e033f4810a', '童年居住的社区'),
    ('ielts_group_d504126391ec19aebec0a0dd', '想参观的大城市'),
    ('ielts_group_93908430e09922123225cb90', '喜欢的纪念碑'),
    ('ielts_group_87f007ac09d2aab198c8817f', '匆忙完成的事情'),
    ('ielts_group_f817d2aab5b23c5c769471f6', '学会制作的食物或饮料'),
    ('ielts_group_358ebc2ab4dc75e4a754717b', '推荐的本国景点'),
    ('ielts_group_8a3a7794d34f5c848f45317f', '拜访亲人的家'),
    ('ielts_group_07ef8811b7c8bc30e44f5e8b', '初识后成为好友的人'),
    ('ielts_group_3941dfe59fa9ef46cfbf514a', '一项好法律'),
    ('ielts_group_4911a974930a69e3b0c65383', '获奖的人'),
    ('ielts_group_cf271b1b57f37b5e25dcbcdb', '超时的汽车旅程'),
    ('ielts_group_bc3f8dba11e4bde21e48cb76', '风景优美的地方'),
    ('ielts_group_c7b9e7d256bbe46d29d9264d', '想重看的戏剧或电影'),
    ('ielts_group_2cfa04d72a3a866c0ac96c77', '改变计划的经历'),
    ('ielts_group_3ff18c7c784776d56a7b6b2c', '满意的工作或学习成果'),
    ('ielts_group_392aba00ef18c66d2f0e6f38', '关于新闻的长谈'),
    ('ielts_group_76536b1403c1a9b391ee805e', '钦佩的知名人士'),
    ('ielts_group_45a2c2796902a4f0c9d3f791', '喜欢的歌曲或音乐'),
    ('ielts_group_e1ec64901a15a914a970d2e0', '一位朋友'),
    ('ielts_group_66cbbb6619acf1e96804021d', '重要的节日'),
    ('ielts_group_95dbab4c6e653a8eed8757e7', '喜欢的家人'),
    ('ielts_group_10a31090a1fe3e9c9c12ddc8', '喜欢的健康活动'),
    ('ielts_group_b9a82c8d6e52355734298222', '喜欢的游戏或运动'),
    ('ielts_group_72f239afcc47eb8453cd3268', '重要的人生选择'),
    ('ielts_group_69053798733fcba75dfda9c3', '喜欢的聚会'),
    ('ielts_group_17acb821a6dce8305b7b17c8', '改进工作或学习的想法'),
    ('ielts_group_8621aea614e86e12bfe41274', '参加过的比赛'),
    ('ielts_group_e88102f2ae8f46a99bee4ffa', '有用的电子设备'),
    ('ielts_group_8cdceb8d1e65c5b7171a74f8', '问卷或调查中表达观点'),
    ('ielts_group_99c3910269d1f58c6cee65f9', '喜欢的餐厅'),
    ('ielts_group_523a7248575cb6f10d226585', '难忘的会议'),
    ('ielts_group_179a6b8e70b8d5da0e7dc5c4', '童年认识的家庭朋友'),
    ('ielts_group_1f507310f4b8e6eeddc1449a', '喜欢的露天市场'),
    ('ielts_group_afb4752239c4283306b37e36', '新鲜或刺激的经历'),
    ('ielts_group_0fe700e0b5624994169f8c61', '难忘的旅程'),
    ('ielts_group_209774cb19d30e19e90df3d4', '帮助他人的人')
) AS normalized(id, title)
WHERE topic.id = normalized.id;

UPDATE ielts_topic AS topic
SET title = normalized.title,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('ielts_group_89b9abdd7b46d0d8253397c8', '想见的名人'),
    ('ielts_group_005a31d668b03853bf1e6242', '喜欢画画的孩子'),
    ('ielts_group_8c738e164cd92c0d2a4d7815', '擅长做计划的人'),
    ('ielts_group_bc6d202916eab3142206b28a', '鼓励你保护自然的人'),
    ('ielts_group_d8ff24292b6fff9f5ab9449e', '机智解决问题的人'),
    ('ielts_group_cf6160a22965296aa515c49f', '向他人学习的朋友'),
    ('ielts_group_5fd465d62884772e61e2cca8', '乐于助人的人'),
    ('ielts_group_97dc43f3fa920138a0312ad4', '钦佩有创造力的人'),
    ('ielts_group_9644c7e8d37fb63a67f45378', '在家族企业工作的人'),
    ('ielts_group_959d25366a0b561f849d5d87', '重要的好朋友'),
    ('ielts_group_7b7611d147c83fc70cc4d990', '擅长音乐的朋友'),
    ('ielts_group_6fcb39390e30006a4bc04046', '钦佩的运动员'),
    ('ielts_group_7f44f1cc2a757e8ebd722b3f', '聊得来的有趣老人'),
    ('ielts_group_e83cc3873bea133723d7a581', '劝你做某事的人'),
    ('ielts_group_4303000a4b6a387e59837c52', '喜欢种植的人'),
    ('ielts_group_499790abee5adcf856b2725e', '聪明的人'),
    ('ielts_group_4bc97acacd1a04ecbf6f37f1', '会打扮的朋友'),
    ('ielts_group_adc251eb02156aeb5de71532', '由不喜欢到喜欢的朋友'),
    ('ielts_group_fc5d106ff73d9da34fbd31a9', '激励你的人'),
    ('ielts_group_b26349385bf7f78b8d401e9a', '童年好友'),
    ('ielts_group_7fe014e2d7c771e9f66b313b', '不同文化背景的朋友'),
    ('ielts_group_95f5d9267f311875352d9a7b', '穿着特别的人'),
    ('ielts_group_8318b895721aa029d7173276', '喜欢的歌手'),
    ('ielts_group_e9e7f46da0f63cdde80d26a0', '令人失望的电影'),
    ('ielts_group_c8bd69d96abfd70040494473', '理想工作'),
    ('ielts_group_58792ddb6f30c866938d04d4', '生活中离不开的东西'),
    ('ielts_group_338c60eed71de00b4d3f42bb', '最近读过的故事'),
    ('ielts_group_03867c94ab8edea5cae091ce', '常用的程序或应用'),
    ('ielts_group_4290ab61aaf9c70b948cabd7', '想拥有的科技产品'),
    ('ielts_group_0f1e8d2fb6d5d9f6b38be83f', '近期喜欢的电影'),
    ('ielts_group_819c90e608f5b6b1a80795ce', '有趣的建筑'),
    ('ielts_group_2c5a50b9d3d096e8998479a6', '对家庭重要的物品'),
    ('ielts_group_4c45810abc97ab1ee3eef9bf', '花费超出预期的物品'),
    ('ielts_group_0d14dd79c2bc4b7104aa7b48', '短期海外工作'),
    ('ielts_group_43f2d7cd544ea87dfe5be84a', '有用的书'),
    ('ielts_group_eb35437da8d1fb2d1520394a', '想了解的野生动物'),
    ('ielts_group_3693214c0aa7f1cdce7090aa', '家中保存的老物件'),
    ('ielts_group_67c5f5a734c42a69a0e9c8c4', '与亲友享用的晚餐'),
    ('ielts_group_aae7d8453b9c85377aff2efb', '传统故事'),
    ('ielts_group_510009328bb24d5e3b4a57cb', '感兴趣的科学领域'),
    ('ielts_group_dd99614435fa7e17c4027513', '想养成的好习惯'),
    ('ielts_group_0253a07eabe3397f6a7223a0', '想提升的天赋'),
    ('ielts_group_873bc2ea2c966dba7f67670d', '童年喜欢的玩具'),
    ('ielts_group_ea0c04e08cb4e74295791009', '想参观的特别建筑'),
    ('ielts_group_677d7f35a97222f2bf515630', '想观看的体育赛事'),
    ('ielts_group_409ec715f4fe27e845e230fc', '想尝试的户外运动'),
    ('ielts_group_0ddea1f86c576b183489d1ad', '让你自豪的照片'),
    ('ielts_group_55713d27c8ec7dc2b0ca7ff5', '读过的健康文章'),
    ('ielts_group_d57ad6f7d23f88fc59e179e0', '能教给别人的技能'),
    ('ielts_group_8e715e86d73e315eb08e0850', '漂亮的物品'),
    ('ielts_group_55688d69271a4834c07bc64d', '想再看的电影'),
    ('ielts_group_36d4d0d60dd3783fcaac1212', '印象深刻的英语课'),
    ('ielts_group_3d6f14a4b27d9fae2410cfdc', '知名产品的广告'),
    ('ielts_group_8379c0e10f21e0ecfadea3cc', '有趣的小说或故事'),
    ('ielts_group_6d3569de92b254de51afb716', '让你发笑的电影'),
    ('ielts_group_cf8b247afc89b247a4553564', '喜欢的节目'),
    ('ielts_group_0440eb51ac46842607393d7a', '二手物品网站'),
    ('ielts_group_a3ac2b6f9f61b1f88ab3e85c', '天空中的景象'),
    ('ielts_group_37357ae99fe708190088779c', '为家人感到骄傲'),
    ('ielts_group_0cf590be055060d2be1dbf8d', '不能使用手机的场合'),
    ('ielts_group_d9e9aaadd0fab3d569925437', '大家微笑的场合'),
    ('ielts_group_7713dbd09dcf10a1418b9804', '短期停留的外国'),
    ('ielts_group_e4f4c539cb34b08c5e534e54', '给别人建议'),
    ('ielts_group_f1ed32f0077aaaf489dca204', '想进行的公路旅行'),
    ('ielts_group_c754b1c332ff7bf80cb4ebab', '发挥想象力的经历'),
    ('ielts_group_3908eb770657032caaaec4eb', '不喜欢的音乐活动'),
    ('ielts_group_0024b10a3db0acc50da0d03e', '鼓励别人做不愿做的事'),
    ('ielts_group_d21714879f9f77bed58816f1', '想再次经历的远行'),
    ('ielts_group_bc4baf18d52b0c80acd850f4', '弄坏东西的经历'),
    ('ielts_group_4aa4b93fb0dc360742146500', '在他人帮助下做决定'),
    ('ielts_group_ec9534e567a77d053234d857', '突然停电'),
    ('ielts_group_be5f56f9a6b713683e9c2c93', '别人向你道歉'),
    ('ielts_group_b8a3fe64f3708597b1cd990b', '迷路的经历'),
    ('ielts_group_1977fb4887a058c4a68d25df', '第一次使用外语'),
    ('ielts_group_301ea9a8a8be9584db0424b4', '社交媒体上的趣事'),
    ('ielts_group_cb875fcf95a441b8125652ce', '等待特别事情发生'),
    ('ielts_group_2303366c143077794ce8fcfe', '良好的购物服务'),
    ('ielts_group_6986b22ba548d01ebd3c737b', '收到金钱礼物'),
    ('ielts_group_022e19d3c9fb4304d8d515b6', '在校外学到的重要事情'),
    ('ielts_group_4057d20ec0b35fdbcf4295a4', '与他人的分歧'),
    ('ielts_group_80a5758de273cf8d3c519ae1', '与他人共同计划活动'),
    ('ielts_group_0699cac0cebfa3236c74e8e4', '难忘的有趣谈话'),
    ('ielts_group_6df5a3b84a6d3d44938e915b', '与他人分享东西'),
    ('ielts_group_63dcc70d027d31426e4203ab', '决定等待某事'),
    ('ielts_group_5820b13bf6b3c6e302239400', '朋友之间的争执'),
    ('ielts_group_01be986686d8bb8909bfc930', '错过约会'),
    ('ielts_group_9a33be5ae485743d83345b08', '克服困难并成功'),
    ('ielts_group_fda673d134eb28f07cb39201', '一次糟糕的购物经历'),
    ('ielts_group_c21499f97539e9974a44af09', '搜索信息的经历'),
    ('ielts_group_debae34d9e4fddcbf972faa2', '公共场合行为不当的孩子'),
    ('ielts_group_54b6d1e8a44c3451090df54c', '见到大量塑料垃圾'),
    ('ielts_group_42e15983e4dabaf3c131fdb8', '第一次尝试的刺激活动'),
    ('ielts_group_c5bce121a9c9e9c0ba80a800', '家中放松的地方'),
    ('ielts_group_8b4049aaba7a5f1f5f73b0af', '喜欢购物的地方'),
    ('ielts_group_67515ef4724bb38d96b441ce', '自然景点'),
    ('ielts_group_44a865852437524acde8a501', '常去的商店'),
    ('ielts_group_3251ddd0091a4d7ead8501c4', '想再次游览的城市'),
    ('ielts_group_82ced007d4af4bde14dd954f', '热门运动场所'),
    ('ielts_group_400b79029075c35dae7e1810', '推荐给游客的本国景点'),
    ('ielts_group_26519ab2ecd324f67e556f8c', '见到动物的地方'),
    ('ielts_group_6c5ac83fd0ecf305bf48cd44', '进行户外活动的地方'),
    ('ielts_group_2d20ce12149c44aa896e0cf9', '常去的熟人之家'),
    ('ielts_group_50c80f54616cde3a753e4fb8', '安静的地方')
) AS normalized(id, title)
WHERE topic.id = normalized.id;
-- IELTS user settings, generated training content and IELTS-specific reports.
-- ielts.ielts_id identifies the generated IELTS scene. A practice_session
-- links its own session_id to this value through practice_session.scene_id.

CREATE TABLE IF NOT EXISTS user_ielts (
    user_id UUID PRIMARY KEY,
    target_score NUMERIC(3, 1),
    today_completed_count SMALLINT NOT NULL DEFAULT 0,
    preferred_voice VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_ielts_target_score_check
        CHECK (target_score IS NULL OR target_score BETWEEN 0 AND 9),
    CONSTRAINT user_ielts_today_completed_count_check
        CHECK (today_completed_count BETWEEN 0 AND 5),
    CONSTRAINT user_ielts_preferred_voice_check
        CHECK (preferred_voice IS NULL OR BTRIM(preferred_voice) <> '')
);

COMMENT ON TABLE user_ielts IS
'用户 IELTS 配置及当日训练次数；today_completed_count 达到 5 后禁止创建新的 IELTS 练习';

COMMENT ON COLUMN user_ielts.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN user_ielts.target_score IS
'用户目标 IELTS Speaking Band，取值 0 至 9';

COMMENT ON COLUMN user_ielts.today_completed_count IS
'用户今日已完成的 IELTS 练习次数，取值 0 至 5；应用需在业务日切换时统一重置为 0';

COMMENT ON COLUMN user_ielts.preferred_voice IS
'用户在 IELTS 模式下使用的专属音色标识';

CREATE OR REPLACE FUNCTION set_user_ielts_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS user_ielts_set_updated_at ON user_ielts;

CREATE TRIGGER user_ielts_set_updated_at
BEFORE UPDATE ON user_ielts
FOR EACH ROW
EXECUTE FUNCTION set_user_ielts_updated_at();

CREATE TABLE IF NOT EXISTS ielts (
    ielts_id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    mode VARCHAR(24) NOT NULL,
    selected_part VARCHAR(8),
    selected_topic_id VARCHAR(64),
    content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ielts_id_check
        CHECK (BTRIM(ielts_id) <> ''),
    CONSTRAINT ielts_mode_check
        CHECK (mode IN ('PART_PRACTICE', 'MOCK_TEST')),
    CONSTRAINT ielts_selected_part_check
        CHECK (selected_part IS NULL
            OR selected_part IN ('PART_1', 'PART_2', 'PART_3')),
    CONSTRAINT ielts_selected_topic_id_check
        CHECK (selected_topic_id IS NULL
            OR BTRIM(selected_topic_id) <> ''),
    CONSTRAINT ielts_content_object_check
        CHECK (JSONB_TYPEOF(content) = 'object'),
    CONSTRAINT ielts_content_parts_check
        CHECK (
            content ?& ARRAY['part1', 'part2', 'part3']
            AND JSONB_TYPEOF(content -> 'part1') = 'array'
            AND JSONB_TYPEOF(content -> 'part2') = 'array'
            AND JSONB_TYPEOF(content -> 'part3') = 'array'
        )
);

CREATE INDEX IF NOT EXISTS idx_ielts_user_created_at
ON ielts (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ielts_selected_topic
ON ielts (selected_topic_id, created_at DESC)
WHERE selected_topic_id IS NOT NULL;

COMMENT ON TABLE ielts IS
'一次 IELTS 练习及其展示内容；会话消息和逐轮评分分别复用 session_message 与 turn_evaluation';

COMMENT ON COLUMN ielts.ielts_id IS
'生成后的 IELTS 场景 ID；practice_session 通过 scene_id 关联该记录，会话使用独立的 session_id';

COMMENT ON COLUMN ielts.user_id IS
'练习所属用户，逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN ielts.mode IS
'训练模式：PART_PRACTICE 为单 Part 练习，MOCK_TEST 为完整模考';

COMMENT ON COLUMN ielts.selected_part IS
'用户选择的训练 Part；完整模考等不限定单 Part 的模式允许为空';

COMMENT ON COLUMN ielts.selected_topic_id IS
'用户选择的题组 ID；随机题目或涉及多个题组时允许为空';

COMMENT ON COLUMN ielts.content IS
'用于前端统一解析的题目 JSON 快照，结构为 {part1: [{question, recommended_expressions}], part2: [...], part3: [...]}';

CREATE OR REPLACE FUNCTION set_ielts_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS ielts_set_updated_at ON ielts;

CREATE TRIGGER ielts_set_updated_at
BEFORE UPDATE ON ielts
FOR EACH ROW
EXECUTE FUNCTION set_ielts_updated_at();

CREATE TABLE IF NOT EXISTS ielts_evaluation (
    session_id VARCHAR(64) PRIMARY KEY,
    ielts_id VARCHAR(64) NOT NULL,
    part VARCHAR(8),
    assessment_type VARCHAR(16) NOT NULL,
    overall_band_score NUMERIC(3, 1) NOT NULL,
    fluency_coherence_score NUMERIC(3, 1) NOT NULL,
    lexical_resource_score NUMERIC(3, 1),
    grammatical_range_accuracy_score NUMERIC(3, 1),
    pronunciation_score NUMERIC(3, 1),
    summary TEXT NOT NULL,
    strengths TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    improvements TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    part_evaluations JSONB NOT NULL DEFAULT '[]'::JSONB,
    recommended_expressions TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ielts_evaluation_session_id_check
        CHECK (BTRIM(session_id) <> ''),
    CONSTRAINT ielts_evaluation_overall_band_score_check
        CHECK (overall_band_score BETWEEN 0 AND 9),
    CONSTRAINT ielts_evaluation_fluency_coherence_score_check
        CHECK (fluency_coherence_score BETWEEN 0 AND 9),
    CONSTRAINT ielts_evaluation_lexical_resource_score_check
        CHECK (lexical_resource_score IS NULL
            OR lexical_resource_score BETWEEN 0 AND 9),
    CONSTRAINT ielts_evaluation_grammar_score_check
        CHECK (grammatical_range_accuracy_score IS NULL
            OR grammatical_range_accuracy_score BETWEEN 0 AND 9),
    CONSTRAINT ielts_evaluation_pronunciation_score_check
        CHECK (pronunciation_score IS NULL
            OR pronunciation_score BETWEEN 0 AND 9),
    CONSTRAINT ielts_evaluation_part_check
        CHECK (part IS NULL OR part IN ('PART_1', 'PART_2', 'PART_3')),
    CONSTRAINT ielts_evaluation_assessment_type_check
        CHECK (assessment_type IN ('DIAGNOSTIC', 'FINAL')),
    CONSTRAINT ielts_evaluation_scope_check
        CHECK ((assessment_type = 'FINAL' AND part IS NULL)
            OR (assessment_type = 'DIAGNOSTIC' AND part IS NOT NULL)),
    CONSTRAINT ielts_evaluation_part_evaluations_check
        CHECK (JSONB_TYPEOF(part_evaluations) = 'array'),
    CONSTRAINT ielts_evaluation_summary_check
        CHECK (BTRIM(summary) <> '')
);

-- Compatibility for local databases that applied the earlier IELTS V4 before
-- the upstream V4 achievement migration was introduced. CREATE TABLE IF NOT
-- EXISTS does not add newly introduced columns to that existing table.
ALTER TABLE ielts_evaluation
    ADD COLUMN IF NOT EXISTS ielts_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS part VARCHAR(8),
    ADD COLUMN IF NOT EXISTS assessment_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS lexical_resource_score NUMERIC(3, 1),
    ADD COLUMN IF NOT EXISTS grammatical_range_accuracy_score NUMERIC(3, 1),
    ADD COLUMN IF NOT EXISTS pronunciation_score NUMERIC(3, 1),
    ADD COLUMN IF NOT EXISTS part_evaluations JSONB DEFAULT '[]'::JSONB,
    ADD COLUMN IF NOT EXISTS recommended_expressions TEXT[] DEFAULT ARRAY[]::TEXT[];

WITH evaluation_scope AS (
    SELECT evaluation.session_id,
           practice.scene_id AS ielts_id,
           generated.selected_part,
           ROW_NUMBER() OVER (
               PARTITION BY practice.scene_id
               ORDER BY practice.started_at, practice.session_id
           ) AS part_number
    FROM ielts_evaluation AS evaluation
    JOIN practice_session AS practice
      ON practice.session_id = evaluation.session_id
    JOIN ielts AS generated
      ON generated.ielts_id = practice.scene_id
)
UPDATE ielts_evaluation AS evaluation
SET ielts_id = COALESCE(evaluation.ielts_id, scope.ielts_id),
    part = COALESCE(
        evaluation.part,
        scope.selected_part,
        CASE MOD(scope.part_number - 1, 3)
            WHEN 0 THEN 'PART_1'
            WHEN 1 THEN 'PART_2'
            ELSE 'PART_3'
        END
    ),
    assessment_type = COALESCE(evaluation.assessment_type, 'DIAGNOSTIC'),
    part_evaluations = COALESCE(evaluation.part_evaluations, '[]'::JSONB),
    recommended_expressions = COALESCE(evaluation.recommended_expressions, ARRAY[]::TEXT[])
FROM evaluation_scope AS scope
WHERE scope.session_id = evaluation.session_id;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ielts_evaluation
        WHERE ielts_id IS NULL
           OR assessment_type IS NULL
           OR part_evaluations IS NULL
           OR recommended_expressions IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot upgrade ielts_evaluation because one or more rows cannot be associated with an IELTS practice session';
    END IF;
END
$migration$;

ALTER TABLE ielts_evaluation
    ALTER COLUMN ielts_id SET NOT NULL,
    ALTER COLUMN assessment_type SET NOT NULL,
    ALTER COLUMN part_evaluations SET DEFAULT '[]'::JSONB,
    ALTER COLUMN part_evaluations SET NOT NULL,
    ALTER COLUMN recommended_expressions SET DEFAULT ARRAY[]::TEXT[],
    ALTER COLUMN recommended_expressions SET NOT NULL;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_lexical_resource_score_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_lexical_resource_score_check
            CHECK (lexical_resource_score IS NULL OR lexical_resource_score BETWEEN 0 AND 9);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_grammar_score_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_grammar_score_check
            CHECK (grammatical_range_accuracy_score IS NULL
                OR grammatical_range_accuracy_score BETWEEN 0 AND 9);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_pronunciation_score_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_pronunciation_score_check
            CHECK (pronunciation_score IS NULL OR pronunciation_score BETWEEN 0 AND 9);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_part_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_part_check
            CHECK (part IS NULL OR part IN ('PART_1', 'PART_2', 'PART_3'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_assessment_type_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_assessment_type_check
            CHECK (assessment_type IN ('DIAGNOSTIC', 'FINAL'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_scope_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_scope_check
            CHECK ((assessment_type = 'FINAL' AND part IS NULL)
                OR (assessment_type = 'DIAGNOSTIC' AND part IS NOT NULL));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ielts_evaluation'::REGCLASS
          AND conname = 'ielts_evaluation_part_evaluations_check'
    ) THEN
        ALTER TABLE ielts_evaluation
            ADD CONSTRAINT ielts_evaluation_part_evaluations_check
            CHECK (JSONB_TYPEOF(part_evaluations) = 'array');
    END IF;
END
$migration$;

CREATE INDEX IF NOT EXISTS idx_ielts_evaluation_ielts_scope
ON ielts_evaluation (ielts_id, assessment_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ielts_evaluation_created_at
ON ielts_evaluation (created_at DESC);

COMMENT ON TABLE ielts_evaluation IS
'IELTS 单 Part 诊断或整场模考评分；同一 ielts_id 可关联三个 Part session';

COMMENT ON COLUMN ielts_evaluation.session_id IS
'逻辑关联 practice_session.session_id，并与 session_message、turn_evaluation 使用相同的 session_id';

COMMENT ON COLUMN ielts_evaluation.ielts_id IS
'逻辑关联 ielts.ielts_id，用于聚合同一次完整模考的三个 Part session';

COMMENT ON COLUMN ielts_evaluation.part IS
'DIAGNOSTIC 对应的 Part；FINAL 整场评分为空';

COMMENT ON COLUMN ielts_evaluation.assessment_type IS
'DIAGNOSTIC 为单 Part 诊断，FINAL 为三个 Part 的整场评分';

COMMENT ON COLUMN ielts_evaluation.overall_band_score IS
'IELTS Speaking 总 Band，取值 0 至 9';

COMMENT ON COLUMN ielts_evaluation.fluency_coherence_score IS
'Fluency and Coherence（流利度与连贯性）Band，取值 0 至 9';

COMMENT ON COLUMN ielts_evaluation.summary IS
'本场 IELTS 练习的总结性评价';

COMMENT ON COLUMN ielts_evaluation.strengths IS
'本场 IELTS 练习表现较好的方面';

COMMENT ON COLUMN ielts_evaluation.improvements IS
'本场 IELTS 练习需要改进的方面';

COMMENT ON COLUMN ielts_evaluation.part_evaluations IS
'FINAL 评分包含的各 Part 评分明细 JSON 数组；DIAGNOSTIC 默认为空数组';

COMMENT ON COLUMN ielts_evaluation.recommended_expressions IS
'本次评分汇总的推荐表达';

CREATE OR REPLACE FUNCTION set_ielts_evaluation_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS ielts_evaluation_set_updated_at
ON ielts_evaluation;

CREATE TRIGGER ielts_evaluation_set_updated_at
BEFORE UPDATE ON ielts_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_ielts_evaluation_updated_at();
