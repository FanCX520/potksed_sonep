package com.example.data.local

/**
 * Rich built-in Baicizhan-style offline word banks (200+ words across 中考/高考/四级/六级/日常)
 * with word-root/affix breakdowns, visual scene memory cues, and TXT/CSV custom book importer.
 */
object VocabCatalogExpanded {

    private val palette = longArrayOf(0xFF38BDF8, 0xFFFF6B4A, 0xFF34D399, 0xFFEAB308, 0xFFA78BFA, 0xFFF43F5E)

    private fun pack(
        bookId: String,
        prefix: String,
        rows: List<String>
    ): List<VocabWordEntity> {
        return rows.mapIndexedNotNull { idx, raw ->
            val p = raw.split("|")
            if (p.size < 7) return@mapIndexedNotNull null
            val num = (idx + 1).toString().padStart(3, '0')
            VocabWordEntity(
                id = "${prefix}_$num",
                word = p[0].trim(),
                phonetic = p[1].trim(),
                partOfSpeech = p[2].trim(),
                meaningZh = p[3].trim(),
                mnemonicTag = p[4].trim(),
                rootBreakdown = p[5].trim(),
                visualCue = p[6].trim(),
                exampleEn = p.getOrNull(7)?.trim().orEmpty().ifBlank { "Use \"${p[0].trim()}\" in a clear sentence." },
                exampleZh = p.getOrNull(8)?.trim().orEmpty().ifBlank { "在清晰例句中掌握「${p[3].trim()}」。" },
                bookId = bookId,
                accentHex = palette[idx % palette.size]
            )
        }
    }

    val allExpandedWords: List<VocabWordEntity> by lazy {
        buildList {
            addAll(pack("middle", "ms", middleSchoolRows))
            addAll(pack("highschool", "hsx", highSchoolExtraRows))
            addAll(pack("cet4", "c4x", cet4ExtraRows))
            addAll(pack("cet6", "c6", cet6Rows))
            addAll(pack("daily", "dlx", dailyExtraRows))
        }
    }

    /**
     * Parses custom TXT/CSV wordbooks imported via MT Manager or local file path.
     * Supports formats per line:
     *   1) word,phonetic,meaning,example
     *   2) word\tmeaning
     *   3) word|phonetic|pos|meaning|root
     *   4) word - meaning
     */
    fun parseCustomWordbook(rawText: String, bookId: String = "custom"): List<VocabWordEntity> {
        val result = mutableListOf<VocabWordEntity>()
        val lines = rawText.lines()
        var idx = 0
        for (line in lines) {
            val trimmed = line.trim().removePrefix("\uFEFF")
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            val parts = when {
                trimmed.contains("|") -> trimmed.split("|")
                trimmed.contains("\t") -> trimmed.split("\t")
                trimmed.contains(",") -> trimmed.split(",")
                trimmed.contains(" - ") -> trimmed.split(" - ", limit = 2)
                else -> trimmed.split(Regex("\\s+"), limit = 2)
            }.map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val w = parts[0]
            if (w.equals("word", ignoreCase = true) && idx == 0) continue

            val phonetic: String
            val meaning: String
            val root: String
            val exEn: String
            when {
                parts.size >= 4 -> {
                    phonetic = parts[1]
                    meaning = parts[2]
                    exEn = parts[3]
                    root = parts.getOrNull(4).orEmpty().ifBlank { "自定义导入词条" }
                }
                parts.size == 3 -> {
                    phonetic = if (parts[1].startsWith("/") || parts[1].startsWith("[")) parts[1] else ""
                    meaning = if (phonetic.isNotEmpty()) parts[2] else "${parts[1]} ${parts[2]}"
                    root = "自定义导入词条"
                    exEn = "Practice \"$w\" daily for strong recall."
                }
                parts.size == 2 -> {
                    phonetic = ""
                    meaning = parts[1]
                    root = "自定义导入词条"
                    exEn = "Practice \"$w\" daily for strong recall."
                }
                else -> {
                    phonetic = ""
                    meaning = "自导入词汇（待补充释义）"
                    root = "自定义导入"
                    exEn = w
                }
            }
            val safeId = "imp_${w.lowercase().replace(Regex("[^a-z0-9]"), "")}_${idx}"
            result.add(
                VocabWordEntity(
                    id = safeId,
                    word = w,
                    phonetic = phonetic,
                    partOfSpeech = "",
                    meaningZh = meaning,
                    mnemonicTag = "自定义词书",
                    exampleEn = exEn,
                    exampleZh = meaning,
                    bookId = bookId,
                    accentHex = palette[idx % palette.size],
                    rootBreakdown = root,
                    visualCue = "来自 MT 管理器导入词库"
                )
            )
            idx++
            if (result.size >= 500) break
        }
        return result
    }

    // Format: word|phonetic|pos|meaning|tag|root|visualCue|exampleEn|exampleZh
    private val middleSchoolRows = listOf(
        "achieve|/əˈtʃiːv/|v.|实现；达到|登顶旗帜|a- 加强 + chieve 头|双手高举冲过终点线|Hard work helps you achieve your dream.|努力帮助你实现梦想。",
        "ancient|/ˈeɪnʃənt/|adj.|古老的；古代的|青铜古卷|anci- 之前 + -ent 形容词|长满青苔的千年古城墙|We visited an ancient temple in Xi'an.|我们参观了西安的一座古寺。",
        "balance|/ˈbæləns/|n./v.|平衡；天平|天平稳立|bi- 双 + lance 秤盘|左右两盘砝码精准齐平|Keep a balance between study and rest.|在学习与休息之间保持平衡。",
        "courage|/ˈkʌrɪdʒ/|n.|勇气；胆量|无畏狮心|cour 心 + -age 名词|迎着逆风勇敢迈出第一步|It takes courage to speak in public.|公开演讲需要勇气。",
        "discover|/dɪˈskʌvə/|v.|发现；发觉|揭开面纱|dis- 去除 + cover 盖子|掀开帷幕发现璀璨星图|Scientists discover new species every year.|科学家每年都会发现新物种。",
        "encourage|/ɪnˈkʌrɪdʒ/|v.|鼓励；激励|击掌加油|en- 使 + courage 勇气|拍肩加油让信心倍增|Teachers encourage students to ask questions.|老师鼓励学生多提问。",
        "familiar|/fəˈmɪliə/|adj.|熟悉的；常见的|老友重逢|famil 家庭 + -iar|像家人一样熟悉的面孔|This melody sounds very familiar to me.|这段旋律我听起来非常熟悉。",
        "generous|/ˈdʒenərəs/|adj.|慷慨的；大方的|分享苹果|gener 出生高贵 + -ous|把大块蛋糕大方分给朋友|She is always generous with her time.|她总是慷慨地抽出时间帮人。",
        "honest|/ˈɒnɪst/|adj.|诚实的；正直的|明镜高悬|hon- 荣誉 + -est|清澈如镜的坦诚目光|An honest answer earns lasting trust.|诚实的回答赢得长久信任。",
        "imagine|/ɪˈmædʒɪn/|v.|想象；设想|云端脑洞|imag 图像 + -ine|闭眼脑海中浮现星辰大海|Imagine living on a quiet island.|想象住在一座安静的小岛上。",
        "journey|/ˈdʒɜːni/|n.|旅程；行程|远行背包|journ 日 + -ey|背起行囊踏上晨光公路|Every great journey starts with one step.|千里之行始于足下。",
        "knowledge|/ˈnɒlɪdʒ/|n.|知识；学问|灯塔书山|know 知道 + -ledge|翻开厚重典籍点亮智慧灯|Reading turns curiosity into knowledge.|阅读把好奇心转化为知识。",
        "memory|/ˈmeməri/|n.|记忆；回忆|时光胶片|memor 记住 + -y|泛黄相册定格青春瞬间|Listening aloud improves word memory.|大声朗读能提升单词记忆。",
        "nature|/ˈneɪtʃə/|n.|自然；本性|青山绿水|nat 出生 + -ure|晨雾缭绕的森林与溪流|Walking in nature refreshes the mind.|漫步大自然能提神醒脑。",
        "organize|/ˈɔːɡənaɪz/|v.|组织；整理|井井有条|organ 器官/工具 + -ize|书桌文具按色系整齐排列|Organize your notes before the exam.|考前把你的笔记整理好。",
        "patient|/ˈpeɪʃnt/|adj./n.|耐心的；病人|静待花开|pati 忍受 + -ent|安静守护幼苗慢慢抽芽|Be patient with difficult grammar rules.|面对难懂的语法要保持耐心。",
        "quality|/ˈkwɒləti/|n.|质量；品质|精工印章|qual 性质 + -ity|显微镜下无可挑剔的工艺|Quality matters more than speed.|质量比速度更重要。",
        "respect|/rɪˈspekt/|v./n.|尊重；敬意|鞠躬致意|re- 再 + spect 看|再次仰望表达由衷敬重|We deeply respect our parents and teachers.|我们深深尊重父母和老师。",
        "science|/ˈsaɪəns/|n.|科学；理科|烧杯星轨|sci 知道 + -ence|实验室里发光的玻璃烧杯|Science explains how the universe works.|科学解释宇宙如何运转。",
        "treasure|/ˈtreʒə/|n./v.|珍宝；珍惜|金光宝箱|treas 宝库 + -ure|小心翼翼捧起珍贵回忆|Treasure the friendships you make at school.|珍惜在校园里结下的友谊。",
        "victory|/ˈvɪktəri/|n.|胜利；成功|高举奖杯|vict 征服 + -ory|欢呼声中举起金色奖杯|Teamwork led them to a sweet victory.|团队合作带领他们走向胜利。",
        "whisper|/ˈwɪspə/|v./n.|低语；耳语|轻声细语|拟声词 whisp 轻风|图书馆里凑近耳边轻轻说|Please whisper in the reading room.|在阅览室请轻声说话。",
        "absorb|/əbˈzɔːb/|v.|吸收；吸引|海绵吸水|ab- 离开 + sorb 吸|蓝色海绵瞬间吸干水珠|Plants absorb sunlight to grow.|植物吸收阳光以生长。",
        "bright|/braɪt/|adj.|明亮的；聪明的|朝阳初升|古英语 beorht 光亮|清晨阳光洒满整间教室|A bright future awaits diligent learners.|光明的未来等待勤奋的学习者。",
        "create|/kriˈeɪt/|v.|创造；创作|指尖生花|cre 生长 + -ate|画笔一挥跃然纸上的作品|Students create posters for Earth Day.|同学们为地球日创作海报。",
        "decide|/dɪˈsaɪd/|v.|决定；下决心|果断斩断|de- 下 + cid 切|果断剪断犹豫选定方向|She decided to practice English daily.|她决定每天练习英语。",
        "effort|/ˈefət/|n.|努力；尽力|汗水浇灌|ef- 出 + fort 力量|使出全身力气推开巨石|Consistent effort always pays off.|持续的努力终有回报。",
        "focus|/ˈfəʊkəs/|v./n.|专注；焦点|镜头对焦|拉丁语 focus 炉火中心|相机光圈紧紧锁住唯一主体|Stay focused during the 25-minute timer.|在25分钟番茄钟内保持专注。",
        "growth|/ɡrəʊθ/|n.|成长；增长|破土新芽|grow 生长 + -th 名词|小树苗迎着阳光拔节生长|Mistakes are essential for personal growth.|错误是个人成长不可或缺的部分。",
        "habit|/ˈhæbɪt/|n.|习惯；习性|齿轮连转|hab 拥有 + -it|每天同一时间自动打卡|Good study habits save lots of time.|良好的学习习惯能省下大量时间。"
    )

    private val highSchoolExtraRows = listOf(
        "advocate|/ˈædvəkeɪt/|v./n.|提倡；拥护者|举牌发声|ad- 向 + voc 喊 + -ate|站上讲台为环保大声呼吁|Experts advocate healthy sleep routines.|专家提倡健康的睡眠作息。",
        "allocate|/ˈæləkeɪt/|v.|分配；拨出|切分蛋糕|al- 给 + loc 地方 + -ate|把时间精准切块分配给各科|Allocate thirty minutes to listening daily.|每天分配三十分钟给英语听力。",
        "analyze|/ˈænəlaɪz/|v.|分析；解析|拆解齿轮|ana- 分开 + lyze 解开|用放大镜逐层剖析长难句|Analyze the causes before jumping to conclusions.|在下结论前先分析原因。",
        "anticipate|/ænˈtɪsɪpeɪt/|v.|预期；预料|提前接球|anti- 先 + cip 抓 + -ate|提前预判落点稳稳接住球|We anticipate great progress this semester.|我们预期本学期会有巨大进步。",
        "authentic|/ɔːˈθentɪk/|adj.|真实的；正宗的|防伪火漆|auth 自己 + -entic|盖着原厂火漆印章的真品|Reading authentic news sharpens vocabulary.|阅读地道新闻能磨砺词汇量。",
        "beneficial|/ˌbenɪˈfɪʃl/|adj.|有益的；有利的|阳光雨露|bene 好 + fic 做 + -ial|晨跑让身心焕发活力|Morning exercise is beneficial to memory.|晨练对记忆力大有裨益。",
        "breakthrough|/ˈbreɪkθruː/|n.|突破；重大进展|破壁而出|break 打破 + through 穿过|闪电劈开瓶颈迎来新天地|The lab announced a battery breakthrough.|实验室宣布了一项电池技术突破。",
        "collaborate|/kəˈlæbəreɪt/|v.|合作；协作|拼图合璧|col- 共同 + labor 劳动|两人手握拼图严丝合缝拼合|Classmates collaborate on the science project.|同学们合作完成科学项目。",
        "comprehend|/ˌkɒmprɪˈhend/|v.|理解；领会|豁然开朗|com- 全 + prehend 抓住|把整段文章核心一把抓住|Take time to comprehend the core idea.|花时间领会文章的核心思想。",
        "consequence|/ˈkɒnsɪkwəns/|n.|结果；后果|多米诺骨牌|con- 共同 + sequ 跟随|推倒第一块引发连锁反应|Think carefully about every consequence.|认真思考每一个后果。",
        "cultivate|/ˈkʌltɪveɪt/|v.|培养；耕作|细心灌溉|cult 耕种 + -ivate|每天给兴趣幼苗浇水施肥|Schools aim to cultivate independent thinking.|学校致力于培养独立思考能力。",
        "dedicate|/ˈdedɪkeɪt/|v.|致力于；奉献|全情投入|de- 加强 + dic 说 + -ate|把整段安静夜晚献给阅读|She dedicated her weekend to exam prep.|她把整个周末投入到备考中。",
        "demonstrate|/ˈdemənstreɪt/|v.|演示；证明|黑板演练|de- 完全 + monstr 展示|一步步在黑板上演示公式|Let me demonstrate how the scanner works.|让我演示一下扫描笔如何工作。",
        "discipline|/ˈdɪsəplɪn/|n.|自律；学科|笔直标尺|disc 学习 + -ipline|按计划准时打卡毫不松懈|Self-discipline turns goals into reality.|自律把目标变成现实。",
        "efficient|/ɪˈfɪʃnt/|adj.|高效的；省时的|流线飞驰|ef- 出 + fic 做 + -ient|花一半时间完成双倍任务|Spaced repetition is an efficient method.|间隔重复是一种高效的记忆法。",
        "eliminate|/ɪˈlɪmɪneɪt/|v.|消除；淘汰|一键清空|e- 出 + limin 门槛 + -ate|把干扰项直接扫出门槛外|Eliminate distractions while studying.|学习时要消除各种干扰。",
        "enhance|/ɪnˈhɑːns/|v.|提高；增强|阶梯跃升|en- 使 + hance 高|音量与清晰度瞬间提升一档|Subtle animations enhance user experience.|细腻的动效提升了使用体验。",
        "enthusiasm|/ɪnˈθjuːziæzəm/|n.|热情；热忱|炽热火焰|en- 内 + thus 神灵感|眼里闪耀着热爱与火花|He approached coding with great enthusiasm.|他怀着极大热情投入编程。",
        "evaluate|/ɪˈvæljueɪt/|v.|评估；评价|精准打分|e- 出 + valu 价值 + -ate|对照标准给试卷客观评分|Evaluate your mistakes after every mock test.|每次模考后都要复盘评估错题。",
        "flourish|/ˈflʌrɪʃ/|v.|繁荣；茁壮成长|百花齐放|flour 花 + -ish 动词|春风里满园繁花竞相绽放|Creativity flourishes in a calm space.|创造力在宁静的空间里蓬勃生长。",
        "fundamental|/ˌfʌndəˈmentl/|adj.|基础的；根本的|大楼地基|fund 底部 + -amental|深埋地下的坚固基石|Vocabulary is fundamental to reading fluency.|词汇是阅读流畅度的根基。",
        "generate|/ˈdʒenəreɪt/|v.|产生；生成|风车发电|gener 产生 + -ate|风车转动源源不断输出电能|Brainstorming helps generate fresh ideas.|头脑风暴有助于产生新点子。",
        "hesitate|/ˈhezɪteɪt/|v.|犹豫；踌躇|十字路口|hesit 粘住 + -ate|脚像粘在岔路口迟疑不前|Never hesitate to ask for help.|寻求帮助时不要犹豫。",
        "illustrate|/ˈɪləstreɪt/|v.|阐明；配插图|图文并茂|il- 使 + lustr 光亮 + -ate|用生动的示意图瞬间讲明白|Charts clearly illustrate the memory curve.|图表清晰地阐明了遗忘曲线。",
        "implement|/ˈɪmplɪment/|v.|实施；执行|落地动工|im- 内 + ple 填满|把蓝图一步步变成代码|They implemented a smart anti-lock guard.|他们落地了一套智能防锁机制。",
        "innovative|/ˈɪnəveɪtɪv/|adj.|创新的；革新的|点亮灯泡|in- 内 + nov 新 + -ative|打破常规跳出全新解法|An innovative widget layout saves screen space.|创新的小组件布局节省了屏幕空间。",
        "interpret|/ɪnˈtɜːprɪt/|v.|解释；口译|双语桥梁|inter- 之间 + pret 价格/传达|在两种语言间架起精准桥梁|How do you interpret this classical poem?|你如何赏析这首古诗？",
        "maintain|/meɪnˈteɪn/|v.|维持；保养|悉心维护|main 手 + tain 握住|双手稳稳托住不让坠落|Maintain a steady daily review pace.|保持稳定的每日复习节奏。",
        "motivate|/ˈməʊtɪveɪt/|v.|激励；激发|引擎点火|mot 动 + -ivate|点火启动内在学习引擎|Small daily wins motivate long-term learning.|每天的小胜利激励长期学习。",
        "negotiate|/nɪˈɡəʊʃieɪt/|v.|协商；谈判|握手共识|neg- 不 + oti 闲暇|双方坐下来耐心沟通达成一致|Teams negotiate timelines before launch.|团队在发布前协商时间表。",
        "objective|/əbˈdʒektɪv/|adj./n.|客观的；目标|靶心准星|ob- 朝向 + ject 投掷|不带偏见直指靶心目标|Set a clear objective for each study session.|为每次学习设定清晰目标。",
        "overcome|/ˌəʊvəˈkʌm/|v.|克服；战胜|翻越高峰|over 越过 + come 来|跨过艰难险阻站上山巅|She overcame stage fright with practice.|她通过练习克服了怯场。",
        "potential|/pəˈtenʃl/|n./adj.|潜力；潜在的|蓄势待发|pot 能力 + -ential|弓弦拉满蕴含无限能量|Every student has untapped potential.|每位学生都有尚未发掘的潜力。",
        "priority|/praɪˈɒrəti/|n.|优先事项|置顶红标|prior 先前 + -ity|把最重要的任务标星置顶|Put high-frequency words at top priority.|把高频词放在最高优先级。",
        "reflect|/rɪˈflekt/|v.|反思；反射|水面映月|re- 回 + flect 弯曲|如平湖映月般静心自省|Reflect on what you learned before sleep.|睡前复盘今天学到的内容。",
        "relevant|/ˈreləvənt/|adj.|相关的；切题的|磁铁相吸|re- 再 + lev 举起|紧扣主题的精准素材|Focus on examples relevant to the exam.|聚焦与考试相关的例句。",
        "significant|/sɪɡˈnɪfɪkənt/|adj.|显著的；重要的|里程碑|sign 标记 + fic 做|值得刻在里程碑上的突破|Daily reading brings significant gains.|每日阅读带来显著收获。",
        "strategy|/ˈstrætədʒi/|n.|策略；战略|棋盘布阵|strat 军队 + -egy|深思熟虑落子布局全局|A smart review strategy beats cramming.|聪明的复习策略胜过临时抱佛脚。",
        "transform|/trænsˈfɔːm/|v.|转变；改造|破茧成蝶|trans- 跨越 + form 形态|旧设备焕然一新变身效率神器|PenOS transforms a dictionary pen into a desk clock.|PenOS 把词典笔改造成了桌面时钟。",
        "ultimate|/ˈʌltɪmət/|adj.|最终的；终极的|巅峰之作|ultim 最后 + -ate|登峰造极的终极目标|Clarity is the ultimate sophistication.|极简清晰是终极的优雅。"
    )

    private val cet4ExtraRows = listOf(
        "accumulate|/əˈkjuːmjəleɪt/|v.|积累；积聚|聚沙成塔|ac- 加强 + cumul 堆积|每日十词日积月累成词海|Small daily habits accumulate into mastery.|每天的小习惯终将积累成精通。",
        "adequate|/ˈædɪkwət/|adj.|充足的；足够的|水位达标|ad- 达到 + equ 相等|水杯注满刚好够用|Ensure adequate lighting when reading.|阅读时请保证充足光线。",
        "ambiguous|/æmˈbɪɡjuəs/|adj.|模棱两可的|雾里看花|ambi- 两边 + ag 驱动|指向两个方向让人捉摸不定|Avoid ambiguous wording in essays.|写作文时要避免模棱两可的措辞。",
        "arbitrary|/ˈɑːbɪtrəri/|adj.|任意的；武断的|随手掷骰|arbitr 裁判 + -ary|凭主观随意拍板决定|Rules should be fair rather than arbitrary.|规则应当公平而非武断。",
        "assert|/əˈsɜːt/|v.|断言；主张|坚定发声|as- 向 + sert 联结|言辞恳切明确表达立场|She asserted her viewpoint with solid facts.|她用扎实事实阐明了自己的观点。",
        "attribute|/əˈtrɪbjuːt/|v./n.|归因于；属性|溯源连线|at- 给 + tribut 给予|把成功归功于长期坚持|He attributed his fluency to daily shadowing.|他把流利口语归功于每日跟读。",
        "clarify|/ˈklærəfaɪ/|v.|澄清；阐明|拨云见日|clar 清楚 + -ify 使|一句话拨开迷雾令众人恍然|Examples help clarify abstract concepts.|例句有助于阐明抽象概念。",
        "coincide|/ˌkəʊɪnˈsaɪd/|v.|同时发生；一致|双圈重合|co- 共同 + in + cid 落|两个时间点恰好完美重合|The exam coincides with the rain season.|考试恰好与雨季重合。",
        "compatible|/kəmˈpætəbl/|adj.|兼容的；合得来的|严丝合缝|com- 共 + pati 承受|安卓5.0老硬件也能丝滑兼容|The launcher is compatible with Android 5.0.|该桌面完美兼容安卓 5.0。",
        "compensate|/ˈkɒmpenseɪt/|v.|补偿；弥补|砝码配平|com- 全 + pens 称重|用勤奋补足起点的差距|Good caching compensates for slow networks.|优秀缓存弥补了网络缓慢。",
        "compile|/kəmˈpaɪl/|v.|汇编；编译|装订成册|com- 共 + pile 堆叠|把零散笔记汇总编译成书|She compiled a personal list of idioms.|她整理汇编了一份个人习语表。",
        "conceive|/kənˈsiːv/|v.|构想；设想|脑海蓝图|con- 共 + ceive 拿住|在脑海中孕育出全新创意|It is hard to conceive life without books.|很难想象没有书籍的生活。",
        "contemplate|/ˈkɒntəmpleɪt/|v.|沉思；凝视|静夜冥想|con- 全 + templ 观察空间|静坐窗前凝望星河陷入沉思|White noise helps you contemplate quietly.|白噪音助你安静沉思。",
        "contradict|/ˌkɒntrəˈdɪkt/|v.|反驳；矛盾|针锋相对|contra- 相反 + dict 说|前后两种说法互相冲突|His actions never contradict his promises.|他的行动从不与承诺相矛盾。",
        "crucial|/ˈkruːʃl/|adj.|至关重要的|十字咽喉|cruc 十字路口 + -ial|决定胜负的关键转折点|Reviewing wrong words is crucial for exams.|复习错词本对考试至关重要。",
        "deduce|/dɪˈdjuːs/|v.|推断；演绎|侦探推理|de- 向下 + duc 引导|从细微线索层层推导出真相|You can deduce meaning from context.|你可以根据上下文推断词义。",
        "deliberate|/dɪˈlɪbərət/|adj.|深思熟虑的；故意的|权衡再三|de- 完全 + liber 天平|放在天平上反复斟酌后决定|Every design choice was deliberate.|每一处设计都经过深思熟虑。",
        "derive|/dɪˈraɪv/|v.|源自；提取|寻根溯源|de- 下 + riv 河流|追溯词根如同寻访江河源头|Many English words derive from Latin roots.|许多英文单词源自拉丁词根。",
        "deteriorate|/dɪˈtɪəriəreɪt/|v.|恶化；变差|坡度下滑|deterior 更差 + -ate|久不复习记忆曲线急剧滑坡|Memory will deteriorate without review.|缺乏复习会导致记忆退化。",
        "distort|/dɪˈstɔːt/|v.|扭曲；歪曲|哈哈镜|dis- 离 + tort 扭转|把原本笔直的线条拧弯变形|Poor lighting can distort colors.|糟糕的光线会让色彩失真。",
        "elaborate|/ɪˈlæbərət/|adj./v.|精心制作的；详述|精雕细琢|e- 出 + labor 劳动|耗费心血打磨每一处细节|Could you elaborate on this grammar point?|你能详细讲讲这个语法点吗？",
        "emerge|/ɪˈmɜːdʒ/|v.|浮现；出现|破浪而出|e- 出 + merge 沉没|朝阳从云海之下缓缓升起|New patterns emerge after careful study.|认真研读后新规律便会浮现。",
        "empirical|/ɪmˈpɪrɪkl/|adj.|经验的；实证的|实验记录|empir 经验 + -ical|经过真机实测验证的结论|Empirical tests caught the device bug.|真机实证测试发现了硬件漏洞。",
        "endeavor|/ɪnˈdevə/|n./v.|努力；竭力|攀登雪峰|en- 使 + deavor 职责|倾尽全力向高峰发起冲刺|Learning a language is a rewarding endeavor.|学习语言是一项回报丰厚的努力。",
        "exaggerate|/ɪɡˈzædʒəreɪt/|v.|夸大；夸张|吹成气球|ex- 出 + agger 堆积|把芝麻小事吹成西瓜大|Do not exaggerate minor setbacks.|不要夸大小小的挫折。",
        "explicit|/ɪkˈsplɪsɪt/|adj.|明确的；直白的|白纸黑字|ex- 出 + plic 折叠|把折叠的纸完全展开一目了然|Give the AI explicit instructions.|给 AI 清晰明确的指令。",
        "feasible|/ˈfiːzəbl/|adj.|可行的；行得通的|绿灯通行|feas 做 + -ible 能|经过评估切实可以落地|Offline DSP white noise is highly feasible.|离线算法合成白噪音非常可行。",
        "fluctuate|/ˈflʌktʃueɪt/|v.|波动；起伏|心电波形|fluctu 波浪 + -ate|温度曲线像海浪上下起伏|Temperatures fluctuate sharply in autumn.|秋天气温起伏很大。",
        "hierarchy|/ˈhaɪərɑːki/|n.|层级；等级制度|金字塔阶|hier 神圣 + arch 统治|信息按主次分明排布成阶梯|Clear visual hierarchy speeds up scanning.|清晰的视觉层级加快扫视速度。",
        "hypothesis|/haɪˈpɒθəsɪs/|n.|假设；假说|待证问号|hypo- 下 + thesis 放置|先立下科学猜想再实验验证|Test your hypothesis with real data.|用真实数据检验你的假设。",
        "immerse|/ɪˈmɜːs/|v.|沉浸；使专注|潜入深海|im- 入 + merse 沉|戴上白噪音完全沉浸书海|Immerse yourself in English podcasts.|让自己沉浸在英文播客中。",
        "incentive|/ɪnˈsentɪv/|n.|激励；动机|领跑旗帜|in- 内 + cent 唱|点燃前进动力的正向激励|Streaks provide a strong study incentive.|连续打卡提供了强大的学习动力。",
        "incorporate|/ɪnˈkɔːpəreɪt/|v.|纳入；融合|融为一体|in- 内 + corpor 身体|把新插件无缝融入桌面体系|We incorporated native widgets cleanly.|我们干净地融入了原生小组件。",
        "indispensable|/ˌɪndɪˈspensəbl/|adj.|不可或缺的|核心齿轮|in- 不 + dispens 省去|少了这颗齿轮机器就停转|A good dictionary is indispensable for learners.|好词典对学习者不可或缺。",
        "inevitably|/ɪnˈevɪtəbli/|adv.|不可避免地|必然轨迹|in- 不 + evit 避免|水到渠成顺理成章发生|Steady practice inevitably builds confidence.|持续练习必然会建立自信。"
    )

    private val cet6Rows = listOf(
        "aesthetic|/iːsˈθetɪk/|adj./n.|美学的；审美的|画廊光影|aesthet 感知 + -ic|纯白画廊中克制精准的留白|Apple galleries celebrate clean aesthetic design.|苹果画廊推崇纯净美学设计。",
        "altruistic|/ˌæltruˈɪstɪk/|adj.|利他的；无私的|提灯照人|altru 他人 + -istic|宁可自己多做也要帮他人|Open-source authors often share altruistic work.|开源作者常怀无私分享精神。",
        "ameliorate|/əˈmiːliəreɪt/|v.|改善；减轻|春风化雨|a- 使 + melior 更好|把粗糙卡顿优化得如丝顺滑|Caching helps ameliorate campus Wi-Fi delays.|缓存有效改善了校园网延迟。",
        "anachronism|/əˈnækrənɪzəm/|n.|时代错置|古人看表|ana- 错 + chron 时间|古代画卷里突兀出现现代物件|Using floppy disks today feels like an anachronism.|今天用软盘显得格格不入。",
        "articulate|/ɑːˈtɪkjuleɪt/|v./adj.|清晰表达；口齿伶俐|字字珠玑|articul 关节 + -ate|思路像精密关节般流畅衔接|She can articulate complex ideas simply.|她能把复杂想法表达得浅显易懂。",
        "assimilate|/əˈsɪməleɪt/|v.|吸收；同化|融会贯通|as- 向 + simil 相似|把新知识彻底消化成自己的|Reviewing helps you assimilate new phrases.|复习帮助你彻底吸收新短语。",
        "brevity|/ˈbrevəti/|n.|简短；简洁|一语中的|brev 短 + -ity|寥寥数语道破精华所在|Brevity is the soul of a strip display UI.|简洁是窄条屏 UI 的灵魂。",
        "candid|/ˈkændɪd/|adj.|坦率的；直言不讳的|清白如雪|cand 洁白 + -id|坦诚直率没有任何掩饰|Thank you for your candid device feedback.|感谢你坦诚的真机测试反馈。",
        "circumspect|/ˈsɜːkəmspekt/|adj.|慎重的；周密的|环顾四周|circum- 周围 + spect 看|行动前环视全局细心谋划|Be circumspect when modifying system apps.|修改系统应用时务必慎重周密。",
        "clandestine|/klænˈdestɪn/|adj.|秘密的；隐蔽的|密室暗门|clam 秘密 + -destine|点五下时间长按才现身的密室|A clandestine gesture unlocks the hidden vault.|一道隐秘手势解锁隐藏密室。",
        "cognizant|/ˈkɒɡnɪzənt/|adj.|意识到的；认知的|心如明镜|cogn 知道 + -izant|对内存与电量消耗了然于胸|Stay cognizant of RAM limits on 1GB pens.|时刻留意 1GB 内存笔的资源上限。",
        "complacent|/kəmˈpleɪsnt/|adj.|自满的；沾沾自喜的|止步不前|com- 全 + plac 满意|满足于现状而停止打磨|Never grow complacent after one high score.|切忌一次高分就沾沾自喜。",
        "concise|/kənˈsaɪs/|adj.|简明的；言简意赅的|精剪枝叶|con- 全 + cise 切|剪去冗余字句只留核心|Write concise notes that fit a small card.|写下能放进小卡片的精简笔记。",
        "conducive|/kənˈdjuːsɪv/|adj.|有益于；有助于|顺风助力|con- 共 + duc 引导|柔和白噪音营造绝佳专注氛围|Rain sounds are conducive to deep focus.|雨声非常有助于深度专注。",
        "corroborate|/kəˈrɒbəreɪt/|v.|证实；佐证|双重印证|cor- 加强 + robor 坚强|多方证据互相印证牢不可破|Logs corroborate the activity hijack bug.|日志证实了 Activity 劫持问题。",
        "culminate|/ˈkʌlmɪneɪt/|v.|达到顶点；告终|登临绝顶|culmin 顶峰 + -ate|数月努力在考场迎来巅峰|Months of study culminate in exam confidence.|数月积累在考场汇聚成自信。",
        "deft|/deft/|adj.|灵巧的；熟练的|行云流水|古英语 dæfte 温柔恰当|手指一滑丝滑切出控制中心|A deft swipe opens the focus timer.|轻轻一滑即可唤出番茄专注。",
        "dichotomy|/daɪˈkɒtəmi/|n.|二分法；鲜明对立|黑白双面|dicho- 两半 + tomy 切|暗黑与纯白画廊的双面美学|The launcher embraces a light-dark dichotomy.|桌面兼顾明暗双面美学。",
        "discerning|/dɪˈsɜːnɪŋ/|adj.|有眼光的；敏锐的|火眼金睛|dis- 分 + cern 筛分|一眼挑出最精细的排印细节|Discerning users notice font consistency.|敏锐的用户会一眼察觉字体统一。",
        "ebullient|/ɪˈbʌliənt/|adj.|热情洋溢的|沸腾泉水|e- 出 + bull 沸腾|像温泉般源源不断洋溢活力|Her ebullient tone brightened the class.|她热情洋溢的语调点亮了课堂。",
        "eclectic|/ɪˈklektɪk/|adj.|兼容并蓄的；精选的|博采众长|ec- 出 + lect 挑选|汇聚苹果待机与有道扫学之长|An eclectic set of modular widgets.|一套博采众长的模块化小组件。",
        "equanimity|/ˌekwəˈnɪməti/|n.|镇定；泰然自若|心静如水|equ 平衡 + anim 心灵|面对难题依旧波澜不惊|Face tough exams with calm equanimity.|以平静从容的心态面对难题。",
        "erudite|/ˈerudaɪt/|adj.|博学的；有学问的|满腹经纶|e- 出 + rud 粗野|饱读诗书谈吐儒雅博学|Poetry cards nurture an erudite mind.|每日诗词滋养博学的心境。",
        "fastidious|/fæˈstɪdiəs/|adj.|挑剔的；一丝不苟的|像素洁癖|fastid 厌恶粗糙|对每1dp圆角和间距都精益求精|Fastidious polish makes software feel alive.|极致打磨让软件充满生命力。",
        "fortitude|/ˈfɔːtɪtjuːd/|n.|坚毅；刚毅|百折不挠|fort 强壮 + -itude|寒窗苦读中展现的钢铁意志|Mental fortitude carries you through finals.|坚毅的心志陪你走过期末考。",
        "impeccable|/ɪmˈpekəbl/|adj.|无可挑剔的；完美的|白璧无瑕|im- 无 + pecc 过失|零误差零瑕疵的精致质感|The serif typography looks impeccable.|思源宋体排印显得无可挑剔。",
        "ingenuity|/ˌɪndʒəˈnjuːəti/|n.|巧思；创造力|巧夺天工|in- 内 + gen 产生|在640x172窄屏上玩出大智慧|Small screens reward engineering ingenuity.|极窄屏幕更考验工程巧思。",
        "laconic|/ləˈkɒnɪk/|adj.|简洁的；惜字如金的|言简意深|源自斯巴达拉科尼亚|寥寥数词却信息满满|Keep status banners clear and laconic.|让状态提示保持简明干练。",
        "magnanimous|/mæɡˈnænɪməs/|adj.|宽宏大量的|海纳百川|magn 大 + anim 胸怀|心胸开阔如大海般宽容|A magnanimous mentor forgives honest errors.|宽厚的导师宽容诚实的失误。",
        "nuance|/ˈnjuːɑːns/|n.|细微差别|丝毫之韵|法语 nue 云彩阴影|两套主题壁纸间微妙的光影层次|Notice the subtle nuance in spring curves.|体会弹簧曲线里微妙的节奏感。",
        "perspicacious|/ˌpɜːspɪˈkeɪʃəs/|adj.|洞察力敏锐的|明察秋毫|per- 穿透 + spic 看|一眼看穿同包名多 Activity 的本质|A perspicacious diagnosis solved the hijack.|敏锐的诊断彻底攻克了劫持难题。",
        "quintessential|/ˌkwɪntɪˈsenʃl/|adj.|典范的；精髓的|经典之最|quint 第五 + essence 本质|凝聚极简美学最纯粹的精髓|White space is the quintessential gallery trait.|留白是画廊美学的典范精髓。",
        "sagacious|/səˈɡeɪʃəs/|adj.|睿智的；明智的|智者远见|sag 敏锐感知 + -acious|深谋远虑做出最佳权衡|Choosing ICP-filed APIs is a sagacious call.|选用国内备案接口是明智之举。",
        "synergy|/ˈsɪnədʒi/|n.|协同效应|一加一大于二|syn- 共 + erg 能量|番茄钟与白噪音联动双倍专注|Pomodoro and rain noise create study synergy.|番茄钟与雨声产生极佳的专注协同。",
        "zenith|/ˈzenɪθ/|n.|顶点；巅峰|正午天顶|阿拉伯语 samt 天顶|精力与效率抵达全天最高峰|Hit your study zenith with zero distractions.|零干扰助你抵达学习巅峰状态。"
    )

    private val dailyExtraRows = listOf(
        "cadence|/ˈkeɪdns/|n.|节奏；韵律|节拍律动|cad 落下 + -ence|如节拍器般舒适自然的律动|Find a study cadence that suits you.|找到适合你的学习节奏。",
        "curate|/kjʊəˈreɪt/|v.|策展；精选|画廊策展|cur 照料 + -ate|像策展人一样只摆最常用软件|Curate only essential apps on your home screen.|只在桌面精选最常用的软件。",
        "fluid|/ˈfluːɪd/|adj.|流畅的；流动的|行云流水|flu 流动 + -id|丝般顺滑的非线性弹性过渡|Spring curves deliver fluid animations.|弹簧曲线带来行云流水的动效。",
        "glanceable|/ˈɡlɑːnsəbl/|adj.|一眼可读的|一瞥即知|glance 一瞥 + -able|抬眼半秒就看清时间与天气|StandBy widgets must stay instantly glanceable.|待机组件必须做到一眼可读。",
        "luminous|/ˈluːmɪnəs/|adj.|发光的；明晰的|微光流转|lumin 光 + -ous|暗夜中柔和不刺眼的微光时钟|A luminous clock glows softly at night.|微光时钟在夜晚柔和亮起。",
        "mindful|/ˈmaɪndfl/|adj.|专注当下的；留心的|心无旁骛|mind 心 + -ful|专注于当下这一页书与这一刻|Stay mindful during your focus session.|在专注时段里保持心无旁骛。",
        "modular|/ˈmɒdjələ/|adj.|模块化的|乐高积木|modul 模块 + -ar|像积木一样按需拆装小组件|Modular plugins keep the launcher extensible.|模块化插件让桌面易于扩展。",
        "nimble|/ˈnɪmbl/|adj.|敏捷的；轻快的|轻盈飞跃|古英语 nǣmel 快速抓握|1GB 内存上依旧秒响应不卡顿|Lean code keeps the pen nimble and fast.|精简代码让词典笔轻快敏捷。",
        "respite|/ˈrespaɪt/|n.|短暂休息；喘息|五分钟小憩|re- 再 + spit 看|番茄钟结束后的五分钟惬意小憩|Enjoy a five-minute respite after focus.|专注结束后享受五分钟小憩。",
        "tactile|/ˈtæktaɪl/|adj.|触觉的；有手感的|回弹质感|tact 触碰 + -ile|每次按下都有微微回弹的质感|Press-scale feedback feels crisp and tactile.|按压缩放反馈带来清脆手感。",
        "zen|/zen/|n./adj.|禅意；极静|静水深流|禅宗 zen|剥离喧嚣后的极致纯粹桌面|Enter Zen mode with full-screen wallpaper.|在全屏壁纸下进入禅意专注。",
        "ambient|/ˈæmbiənt/|adj.|环境氛围的|柔和雨声|ambi- 周围 + ent|淡淡的细雨声包裹整个书桌|Ambient rain masks dormitory noise.|环境雨声遮蔽宿舍杂音。",
        "horizon|/həˈraɪzn/|n.|地平线；视野|远方天际|horiz 界限|开阔视野望向更远的世界|Every new word widens your horizon.|每一个新词都拓宽你的视野。",
        "spark|/spɑːk/|n./v.|火花；激发|灵感迸发|古英语 spearca|一句好诗瞬间点亮写作灵感|A good poem can spark fresh writing ideas.|一句好诗能点燃写作灵感。",
        "anchor|/ˈæŋkə/|n./v.|锚；定心柱|稳稳下锚|希腊语 ankyra 船锚|把时间与计划稳稳锚定在桌面|Let the desktop clock anchor your daily plan.|让桌面时钟锚定你的每日计划。",
        "breeze|/briːz/|n.|微风；轻而易举|林间清风|西班牙语 brisa|像微风拂面般轻松背完单词|With daily review, exams become a breeze.|每天复习让考试变得轻松自如。",
        "clarity|/ˈklærəti/|n.|清晰度；明澈|纤毫毕现|clar 清晰 + -ity|思源宋体在小屏上依旧清晰锐利|High contrast improves reading clarity.|高对比度提升阅读清晰度。",
        "delight|/dɪˈlaɪt/|n./v.|愉悦；惊喜|会心一笑|de- 离 + light 轻盈|细腻的交互细节让人会心一笑|Thoughtful details bring everyday delight.|用心的细节带来日常愉悦。",
        "haven|/ˈheɪvn/|n.|避风港；静谧之所|安心书桌|古英语 hæfen 港湾|把词典笔打造成零打扰学习港湾|Turn your desk into a distraction-free haven.|把书桌打造成免打扰学习港湾。",
        "poise|/pɔɪz/|n.|优雅从容；平衡|从容不迫|拉丁语 pensum 重量|画面比例与留白尽显从容优雅|Clean spacing gives the interface quiet poise.|清爽留白赋予界面从容质感。"
    )
}
