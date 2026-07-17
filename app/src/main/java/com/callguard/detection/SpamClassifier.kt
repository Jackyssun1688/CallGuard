package com.callguard.detection

/**
 * 中文骚扰电话文本分类器。
 *
 * 使用关键词匹配 + 加权打分来识别常见的骚扰/诈骗/推销/自动语音。
 * 纯本地运行，无需网络，保护隐私。
 */
class SpamClassifier {

    // ========== 关键词分类表 ==========

    /** 贷款/金融诈骗类 */
    private val loanKeywords = listOf(
        "贷款", "借钱", "借呗", "花呗", "白条", "网贷", "信用贷", "微粒贷",
        "周转", "资金", "利息", "低息", "高额", "额度", "放款", "秒批",
        "无视黑白", "不看征信", "征信", "逾期", "催收", "信用卡", "套现",
        "抵押", "无抵押", "担保", "车贷", "房贷", "消费贷", "教育贷",
        "培训贷", "美容贷", "京东金条", "有钱花", "360借条", "分期",
        "最低还款", "手续费", "免息", "零利息", "急用钱", "缺钱"
    )

    /** 推销/广告类 */
    private val promoKeywords = listOf(
        "促销", "打折", "优惠", "特价", "免费", "赠送", "抽奖", "中奖",
        "领奖", "获奖", "一等奖", "二等奖", "送手机", "送话费", "送流量",
        "送礼品", "免费体验", "试用", "样品", "会员", "VIP", "白金",
        "升级", "续费", "套餐", "合约", "宽带", "光纤", "5G升级",
        "办卡", "信用卡", "保险", "理财", "基金", "股票", "推荐",
        "商铺", "旺铺", "投资", "加盟", "代理", "区域代理", "直销",
        "传销", "上课", "讲座", "培训", "课程", "英语课", "试听",
        "买房", "卖房", "楼盘", "车位", "装修", "建材", "家具",
        "家居", "家电", "汽车", "试驾", "保养", "保险到期", "保险升级",
        "教育", "少儿", "编程", "围棋", "美术", "钢琴", "补课",
        "家教", "留学", "移民", "签证", "旅游", "跟团", "酒店",
        "门票", "健身房", "瑜伽", "美容", "减肥", "整容", "植发",
        "牙科", "体检", "基因检测", "保健品", "虫草", "燕窝", "人参"
    )

    /** 诈骗/欺诈类 */
    private val scamKeywords = listOf(
        "公安局", "公安", "警察", "法院", "检察院", "检察院", "法官",
        "律师", "传票", "通缉令", "逮捕令", "洗钱", "涉毒", "走私",
        "涉案", "案件", "调查", "取证", "保证金", "安全账户",
        "冻结", "查封", "停用", "停卡", "异常", "风险", "违规",
        "被封", "注销", "失效", "过期", "即将到期", "最后一天",
        "马上", "立刻", "立即", "紧急", "加急", "特急",
        "亲属", "家人", "出事", "车祸", "急诊", "住院", "手术",
        "打款", "转账", "汇款", "网银", "验证码", "密码", "卡号",
        "账号", "免密", "快捷支付", "退款", "理赔", "赔偿",
        "保证金", "押金", "解冻", "激活", "充值", "发红包",
        "扫码", "二维码", "链接", "点击", "下载", "安装",
        "屏幕共享", "远程协助", "操作指导", "客服", "假客服",
        "京东客服", "淘宝客服", "支付宝客服", "银行客服",
        "微信客服", "腾讯客服", "蚂蚁客服", "官方客服",
        "取消会员", "取消代理", "取消业务", "解约", "背离",
        "征信修复", "信用修复", "洗白", "黑户", "修复征信",
        "扶贫", "助农", "补贴", "退税", "医疗补贴", "社保补贴",
        "生育补贴", "丧葬费", "抚恤金",
        "比特币", "数字货币", "区块链", "挖矿", "USDT", "虚拟币",
        "外汇", "期货", "贵金属", "原油", "现货",
        "刷单", "刷信誉", "兼职", "日结", "在家工作", "轻松",
        "手工活", "串珠", "组装", "加工",
        "杀猪盘", "网恋", "交友", "征婚", "富婆", "富豪",
        "老中医", "祖传", "偏方", "神药", "特效药", "根治",
        "数据恢复", "定位", "查通话", "查聊天", "查开房",
        "银行卡", "储蓄卡", "信用卡升级", "白金卡", "钻石卡",
        "额度提升", "提额", "降息", "调额"
    )

    /** 自动语音/机器人特征 */
    private val autoVoiceKeywords = listOf(
        "您好", "你好", "我是", "这里是", "您有", "为您",
        "请问是", "先生/女士", "女士/先生",
        "按1", "按2", "按0", "按9",
        "人工服务请按", "转人工", "返回菜单",
        "听到滴声后", "滴声后", "请留言",
        "开始录音", "本次通话", "将被录音",
        "为了给您", "为了提供", "根据您的", "根据系统",
        "您可以选择", "请选择", "请按键",
        "重听请按", "返回请按", "结束请挂机",
        "已为您", "正在为您", "即将为您",
        "我们检测到", "系统检测到", "大数据显示",
        "您的号码", "您的账户", "您的订单",
        "关于您的", "针对您的", "根据最新"
    )

    /** 加权权重 */
    private data class CategoryWeight(
        val name: String,
        val keywords: List<String>,
        val weight: Float
    )

    private val categories = listOf(
        CategoryWeight("loan", loanKeywords, 2.0f),
        CategoryWeight("scam", scamKeywords, 3.0f),  // 诈骗权重最高
        CategoryWeight("promo", promoKeywords, 1.5f),
        CategoryWeight("auto_voice", autoVoiceKeywords, 1.0f)
    )

    private val allKeywords = categories.flatMap { it.keywords }.toSet()

    /** 最短文本长度才进行检测 */
    private val minTextLength = 5

    /**
     * 对识别到的文本进行分类。
     * @param text 语音识别转写的文字内容
     * @return 分类结果
     */
    fun classify(text: String): ClassificationResult {
        if (text.length < minTextLength) {
            return ClassificationResult(
                isSpam = false,
                category = "",
                confidence = 0f
            )
        }

        val lowerText = text.lowercase()

        // 计算每个类别的得分
        val scores = mutableMapOf<String, Float>()
        val matchedAll = mutableListOf<String>()

        for (cat in categories) {
            var score = 0f
            val matched = mutableListOf<String>()

            for (kw in cat.keywords) {
                if (lowerText.contains(kw.lowercase())) {
                    score += cat.weight
                    matched.add(kw)
                    matchedAll.add(kw)
                }
            }

            if (matched.isNotEmpty()) {
                scores[cat.name] = score
            }
        }

        if (scores.isEmpty()) {
            return ClassificationResult(
                isSpam = false,
                category = "",
                confidence = 0f,
                matchedKeywords = emptyList()
            )
        }

        // 取最高分类别
        val bestCategory = scores.maxByOrNull { it.value }!!
        val totalScore = bestCategory.value

        // 计算置信度 (0~1)，线性映射到 0.5~1.0 区间
        val rawConfidence = 0.5f + (minOf(totalScore, 10f) / 10f) * 0.5f
        val confidence = minOf(rawConfidence, 1.0f)

        // 短文本 + 低分 => 可能误判，降低置信度
        val lengthBonus = minOf(1.0f, text.length.toFloat() / 100f)
        val adjustedConfidence = confidence * (0.7f + 0.3f * lengthBonus)

        val isSpam = adjustedConfidence >= 0.5f

        return ClassificationResult(
            isSpam = isSpam,
            category = bestCategory.key,
            confidence = adjustedConfidence.coerceIn(0f, 1f),
            matchedKeywords = matchedAll
        )
    }

    /**
     * 判断文本是否包含任何已知骚扰关键词。
     */
    fun containsAnyKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return allKeywords.any { lower.contains(it.lowercase()) }
    }
}
