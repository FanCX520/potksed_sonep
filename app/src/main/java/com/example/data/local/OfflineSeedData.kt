package com.example.data.local

object OfflineSeedData {

    val defaultPoems: List<CachedPoemEntity> = listOf(
        CachedPoemEntity(
            id = "offline_1",
            content = "疏影横斜水清浅，暗香浮动月黄昏。",
            title = "山园小梅·其一",
            dynasty = "宋代",
            author = "林逋",
            fullContent = "众芳摇落独暄妍，占尽风情向小园。疏影横斜水清浅，暗香浮动月黄昏。",
            fetchedAt = 1_700_000_001L
        ),
        CachedPoemEntity(
            id = "offline_2",
            content = "醉后不知天在水，满船清梦压星河。",
            title = "题龙阳县青草湖",
            dynasty = "元代",
            author = "唐温如",
            fullContent = "西风吹老洞庭波，一夜湘君白发多。醉后不知天在水，满船清梦压星河。",
            fetchedAt = 1_700_000_002L
        ),
        CachedPoemEntity(
            id = "offline_3",
            content = "长风破浪会有时，直挂云帆济沧海。",
            title = "行路难·其一",
            dynasty = "唐代",
            author = "李白",
            fullContent = "行路难，行路难，多歧路，今安在？长风破浪会有时，直挂云帆济沧海。",
            fetchedAt = 1_700_000_003L
        ),
        CachedPoemEntity(
            id = "offline_4",
            content = "人生如逆旅，我亦是行人。",
            title = "临江仙·送钱穆父",
            dynasty = "宋代",
            author = "苏轼",
            fullContent = "一别都门三改火，天涯踏尽红尘。依然一笑作春温。无波真古井，有节是秋筠。",
            fetchedAt = 1_700_000_004L
        ),
        CachedPoemEntity(
            id = "offline_5",
            content = "君问归期未有期，巴山夜雨涨秋池。",
            title = "夜雨寄北",
            dynasty = "唐代",
            author = "李商隐",
            fullContent = "君问归期未有期，巴山夜雨涨秋池。何当共剪西窗烛，却话巴山夜雨时。",
            fetchedAt = 1_700_000_005L
        ),
        CachedPoemEntity(
            id = "offline_6",
            content = "竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。",
            title = "定风波",
            dynasty = "宋代",
            author = "苏轼",
            fullContent = "莫听穿林打叶声，何妨吟啸且徐行。竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。",
            fetchedAt = 1_700_000_006L
        ),
        CachedPoemEntity(
            id = "offline_7",
            content = "落霞与孤鹜齐飞，秋水共长天一色。",
            title = "滕王阁序",
            dynasty = "唐代",
            author = "王勃",
            fullContent = "落霞与孤鹜齐飞，秋水共长天一色。渔舟唱晚，响穷彭蠡之滨。",
            fetchedAt = 1_700_000_007L
        ),
        CachedPoemEntity(
            id = "offline_8",
            content = "采菊东篱下，悠然见南山。",
            title = "饮酒·其五",
            dynasty = "魏晋",
            author = "陶渊明",
            fullContent = "结庐在人境，而无车马喧。问君何能尔？心远地自偏。采菊东篱下，悠然见南山。",
            fetchedAt = 1_700_000_008L
        )
    )

    val defaultWords: List<VocabWordEntity> = listOf(
        // 高中核心 (highschool)
        VocabWordEntity("hs_01", "resilient", "/rɪˈzɪliənt/", "adj.", "有韧性的；能迅速恢复的", "弹簧回弹", "She proved remarkably resilient under pressure.", "她在压力下表现出惊人的韧性。", "highschool", 0xFF38BDF8),
        VocabWordEntity("hs_02", "eloquent", "/ˈeləkwənt/", "adj.", "雄辩的；有说服力的", "出口成章", "He gave an eloquent speech on climate action.", "他发表了一场关于气候行动的动人演讲。", "highschool", 0xFFFF6B4A),
        VocabWordEntity("hs_03", "ambitious", "/æmˈbɪʃəs/", "adj.", "有雄心的；宏大的", "志存高远", "They launched an ambitious space project.", "他们启动了一项宏大的航天计划。", "highschool", 0xFF34D399),
        VocabWordEntity("hs_04", "diligent", "/ˈdɪlɪdʒənt/", "adj.", "勤奋的；孜孜不倦的", "挑灯夜读", "Diligent practice builds lasting fluency.", "勤奋练习能练就持久的流利度。", "highschool", 0xFFEAB308),
        VocabWordEntity("hs_05", "curiosity", "/ˌkjʊəriˈɒsəti/", "n.", "好奇心；求知欲", "探索星空", "Curiosity drives every scientific breakthrough.", "好奇心推动着每一次科学突破。", "highschool", 0xFF38BDF8),
        VocabWordEntity("hs_06", "perspective", "/pəˈspektɪv/", "n.", "视角；观点；透视法", "换位思考", "Reading broadens your perspective on history.", "阅读拓宽你对历史的视角。", "highschool", 0xFFA78BFA),
        VocabWordEntity("hs_07", "sustainable", "/səˈsteɪnəbl/", "adj.", "可持续的；环保的", "绿水青山", "Solar power offers a sustainable future.", "太阳能提供了可持续的未来。", "highschool", 0xFF34D399),
        VocabWordEntity("hs_08", "gratitude", "/ˈɡrætɪtjuːd/", "n.", "感激之情；谢意", "心怀感恩", "We expressed deep gratitude to our teachers.", "我们向老师表达了深深的感激。", "highschool", 0xFFFF6B4A),
        VocabWordEntity("hs_09", "inevitable", "/ɪnˈevɪtəbl/", "adj.", "不可避免的；必然的", "大势所趋", "Change is inevitable as technology evolves.", "随着科技演进，变革是不可避免的。", "highschool", 0xFFEAB308),
        VocabWordEntity("hs_10", "distinct", "/dɪˈstɪŋkt/", "adj.", "明显的；截然不同的", "泾渭分明", "Each dialect has a distinct rhythm.", "每种方言都有鲜明独特的韵律。", "highschool", 0xFF38BDF8),
        VocabWordEntity("hs_11", "harmony", "/ˈhɑːməni/", "n.", "和谐；融洽；和声", "琴瑟和鸣", "Humans should live in harmony with nature.", "人类应当与自然和谐共处。", "highschool", 0xFF34D399),
        VocabWordEntity("hs_12", "obstacle", "/ˈɒbstəkl/", "n.", "障碍；绊脚石", "翻越栏杆", "Every obstacle can become a stepping stone.", "每一个障碍都能成为垫脚石。", "highschool", 0xFFF43F5E),
        VocabWordEntity("hs_13", "vivid", "/ˈvɪvɪd/", "adj.", "生动的；鲜明的；逼真的", "栩栩如生", "She kept vivid memories of that autumn.", "她保留着那个秋天鲜活的记忆。", "highschool", 0xFFFF6B4A),
        VocabWordEntity("hs_14", "cherish", "/ˈtʃerɪʃ/", "v.", "珍惜；珍爱；怀抱", "掌上明珠", "Cherish every quiet hour of study.", "珍惜每一段安静学习的时光。", "highschool", 0xFFEAB308),
        VocabWordEntity("hs_15", "persevere", "/ˌpɜːsɪˈvɪə/", "v.", "坚持不懈；锲而不舍", "水滴石穿", "Persevere even when progress feels slow.", "即便进步缓慢，也要坚持不懈。", "highschool", 0xFF34D399),
        VocabWordEntity("hs_16", "empathy", "/ˈempəθi/", "n.", "共情；同理心", "感同身受", "Empathy connects people across cultures.", "同理心让不同文化的人心意相通。", "highschool", 0xFFA78BFA),

        // 四级高频 (cet4)
        VocabWordEntity("c4_01", "ubiquitous", "/juːˈbɪkwɪtəs/", "adj.", "无处不在的；普遍存在的", "随处可见", "Smartphones have become ubiquitous in daily life.", "智能手机在日常生活中已无处不在。", "cet4", 0xFF38BDF8),
        VocabWordEntity("c4_02", "pragmatic", "/præɡˈmætɪk/", "adj.", "务实的；实用主义的", "脚踏实地", "We need a pragmatic solution to save battery.", "我们需要一个务实的省电方案。", "cet4", 0xFF34D399),
        VocabWordEntity("c4_03", "coherent", "/kəʊˈhɪərənt/", "adj.", "连贯的；条理清晰的", "逻辑闭环", "Please write a coherent summary of the text.", "请写一份条理清晰的文本摘要。", "cet4", 0xFFA78BFA),
        VocabWordEntity("c4_04", "alleviate", "/əˈliːvieɪt/", "v.", "减轻；缓和（痛苦/压力）", "如释重负", "Short breaks help alleviate eye strain.", "短暂休息有助于缓解眼疲劳。", "cet4", 0xFF34D399),
        VocabWordEntity("c4_05", "scrutinize", "/ˈskruːtənaɪz/", "v.", "仔细审查；细看", "放大镜看", "Scientists scrutinize every data point.", "科学家仔细审视每一个数据点。", "cet4", 0xFFEAB308),
        VocabWordEntity("c4_06", "ephemeral", "/ɪˈfemərəl/", "adj.", "短暂的；瞬息即逝的", "朝露昙花", "Morning dew is beautiful yet ephemeral.", "晨露虽美却转瞬即逝。", "cet4", 0xFFFF6B4A),
        VocabWordEntity("c4_07", "catalyst", "/ˈkætəlɪst/", "n.", "催化剂；促使变化的因素", "化学加速", "The invention served as a catalyst for reform.", "这项发明成为了变革的催化剂。", "cet4", 0xFF38BDF8),
        VocabWordEntity("c4_08", "meticulous", "/məˈtɪkjələs/", "adj.", "一丝不苟的；极其细致的", "匠心雕琢", "Her notes were meticulous and well organized.", "她的笔记一丝不苟且条理井然。", "cet4", 0xFFA78BFA),
        VocabWordEntity("c4_09", "versatile", "/ˈvɜːsətaɪl/", "adj.", "多才多艺的；多用途的", "全能多面", "This dictionary pen is compact yet versatile.", "这支词典笔小巧却功能多面。", "cet4", 0xFF34D399),
        VocabWordEntity("c4_10", "profound", "/prəˈfaʊnd/", "adj.", "深远的；深刻的；渊博的", "发人深省", "The poem left a profound impression on me.", "这首诗给我留下了深刻的印象。", "cet4", 0xFFEAB308),
        VocabWordEntity("c4_11", "consensus", "/kənˈsensəs/", "n.", "共识；一致意见", "达成一致", "The team reached a consensus on the design.", "团队就设计方案达成了共识。", "cet4", 0xFF38BDF8),
        VocabWordEntity("c4_12", "diminish", "/dɪˈmɪnɪʃ/", "v.", "减少；削弱；降低", "逐渐变小", "Dark mode helps diminish screen glare at night.", "暗黑模式有助于降低夜间的屏幕眩光。", "cet4", 0xFFF43F5E),
        VocabWordEntity("c4_13", "intrinsic", "/ɪnˈtrɪnzɪk/", "adj.", "内在的；固有的；本质的", "由内而外", "Intrinsic motivation lasts longer than rewards.", "内在动力比外在奖励更持久。", "cet4", 0xFFFF6B4A),
        VocabWordEntity("c4_14", "plausible", "/ˈplɔːzəbl/", "adj.", "貌似合理的；可信的", "合情合理", "That sounds like a plausible explanation.", "这听起来是个合情合理的解释。", "cet4", 0xFFA78BFA),
        VocabWordEntity("c4_15", "lucid", "/ˈluːsɪd/", "adj.", "清晰易懂的；头脑清醒的", "清澈明朗", "He gave a lucid explanation of the algorithm.", "他对这个算法做出了清晰易懂的解释。", "cet4", 0xFF38BDF8),
        VocabWordEntity("c4_16", "tenacious", "/təˈneɪʃəs/", "adj.", "顽强的；坚韧不拔的", "紧抓不放", "Tenacious learners overcome difficult exams.", "顽强的学习者总能攻克高难度考试。", "cet4", 0xFF34D399),

        // 日常实用 (daily)
        VocabWordEntity("dl_01", "serene", "/səˈriːn/", "adj.", "宁静的；安详的", "静谧湖面", "The library feels serene early in the morning.", "清晨的图书馆显得格外宁静。", "daily", 0xFF38BDF8),
        VocabWordEntity("dl_02", "minimalist", "/ˈmɪnɪməlɪst/", "adj.", "极简主义的；简约的", "少即是多", "A minimalist desk improves focus.", "极简的书桌能提升专注力。", "daily", 0xFFEAB308),
        VocabWordEntity("dl_03", "intuitive", "/ɪnˈtjuːɪtɪv/", "adj.", "直观的；凭直觉即可懂的", "一触即会", "Good interfaces feel effortless and intuitive.", "好的界面用起来毫不费力且直观。", "daily", 0xFF34D399),
        VocabWordEntity("dl_04", "crisp", "/krɪsp/", "adj.", "清晰的；干脆利落的；清冽的", "清脆锐利", "The typography looks crisp on a small display.", "这套字体在小屏幕上显得非常锐利。", "daily", 0xFFFF6B4A),
        VocabWordEntity("dl_05", "seamless", "/ˈsiːmləs/", "adj.", "无缝的；流畅衔接的", "天衣无缝", "Cloud sync enables seamless device switching.", "云同步实现了设备间的无缝切换。", "daily", 0xFFA78BFA),
        VocabWordEntity("dl_06", "compact", "/kəmˈpækt/", "adj.", "紧凑的；小巧便携的", "袖珍精致", "A compact gadget fits easily in a pocket.", "小巧的设备可以轻松装进口袋。", "daily", 0xFF38BDF8),
        VocabWordEntity("dl_07", "ambient", "/ˈæmbiənt/", "adj.", "环境的；氛围感的", "微光氛围", "StandBy mode turns your pen into an ambient clock.", "待机模式让你的笔变身氛围时钟。", "daily", 0xFFEAB308),
        VocabWordEntity("dl_08", "momentum", "/məˈmentəm/", "n.", "势头；动量；前进的动力", "趁热打铁", "Keep your daily study momentum going.", "保持你每天学习的势头。", "daily", 0xFFFF6B4A),
        VocabWordEntity("dl_09", "tranquil", "/ˈtræŋkwɪl/", "adj.", "平静的；安静的", "岁月静好", "We walked along a tranquil riverside path.", "我们沿着宁静的河畔小路散步。", "daily", 0xFF34D399),
        VocabWordEntity("dl_10", "refinement", "/rɪˈfaɪnmənt/", "n.", "精炼；微调优化；优雅", "精益求精", "Subtle refinement makes a tool joyful to use.", "细微的打磨让工具用起来赏心悦目。", "daily", 0xFFA78BFA)
    )
}
