package com.unispeaking.component.scene;

import com.unispeaking.domain.vo.scene.DailyPickTopic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DailyPickCatalog {

	private static final List<Variation> VARIATIONS = List.of(
			new Variation("essential", "8–10 分钟", "初级", "完成核心沟通并清楚表达需求", "先完成最常见的基础沟通，确认关键信息"),
			new Variation("problem", "10–12 分钟", "中级", "说明突发问题并协商解决办法", "过程中遇到一个意外问题，需要解释情况并协商解决"),
			new Variation("advanced", "12–15 分钟", "高级", "比较不同选择并进行更深入的交流", "进一步询问细节、比较选择，并确认最终安排")
	);

	private static final List<ScenarioSeed> SEEDS = List.of(
			seed("coffee-shop", "咖啡店点单", "food", "在咖啡店选择饮品，说明杯型、温度和甜度"),
			seed("restaurant-order", "餐厅点餐", "food", "在餐厅查看菜单、询问菜品并完成点餐"),
			seed("food-allergy", "说明食物过敏", "food", "就餐时说明食物过敏和饮食限制"),
			seed("takeaway-order", "电话订餐", "food", "打电话订餐并确认菜品、地址和送达时间"),
			seed("bakery-shopping", "面包店选购", "food", "在面包店询问口味、配料和保质期"),
			seed("buffet-dining", "自助餐咨询", "food", "在自助餐厅询问用餐规则和可选食物"),
			seed("barbecue-party", "烧烤聚餐", "food", "和朋友准备烧烤，商量食材、分工和时间"),
			seed("business-dinner", "商务晚餐", "food", "参加商务晚餐并兼顾点餐礼仪和席间交流"),
			seed("cooking-class", "烹饪课程", "food", "参加烹饪课程，询问步骤、食材和操作方法"),
			seed("food-delivery", "外卖订单", "food", "联系外卖平台确认订单、配送进度和特殊要求"),

			seed("clothing-store", "服装店选购", "shopping", "在服装店寻找合适的款式、颜色和尺码"),
			seed("clothing-return", "服装退换", "shopping", "说明服装不合适并询问退换流程"),
			seed("electronics-store", "选购电子产品", "shopping", "比较电子产品的功能、价格和保修服务"),
			seed("supermarket", "超市购物", "shopping", "在超市寻找商品并核对规格、成分和促销"),
			seed("bookstore", "书店选书", "shopping", "说明阅读偏好并请店员推荐合适的书"),
			seed("market-bargain", "市场询价", "shopping", "在市场询问商品材质、规格和价格"),
			seed("gift-shopping", "挑选礼物", "shopping", "根据收礼人的喜好和预算挑选礼物"),
			seed("furniture-store", "家具店咨询", "shopping", "询问家具尺寸、材质、配送和安装服务"),
			seed("online-refund", "网购退款", "shopping", "联系网店客服说明商品问题并申请退款"),
			seed("secondhand-trade", "二手交易", "shopping", "和卖家确认二手商品的状况、价格和交付方式"),

			seed("airport-checkin", "机场值机", "transit", "在机场办理值机并确认座位、行李和登机信息"),
			seed("flight-delay", "航班延误", "transit", "向机场工作人员询问延误和替代安排"),
			seed("train-ticket", "购买火车票", "transit", "在车站购买车票并确认班次、座位和站台"),
			seed("train-change", "火车票改签", "transit", "申请改签车票并确认可选班次和费用"),
			seed("directions", "问路与换乘", "transit", "向路人询问路线和公共交通换乘方式"),
			seed("taxi-ride", "乘坐出租车", "transit", "向司机说明目的地、路线偏好和下车位置"),
			seed("car-rental", "租车取车", "transit", "在租车柜台确认车型、保险和还车要求"),
			seed("bus-pass", "办理公交卡", "transit", "咨询公交卡价格、充值方式和适用范围"),
			seed("bike-rental", "租用自行车", "transit", "询问自行车租用、计费和归还规则"),
			seed("missed-connection", "错过交通接驳", "transit", "向工作人员说明错过接驳的情况并寻找替代路线"),

			seed("hotel-checkin", "酒店入住", "accommodation", "在酒店前台办理入住并确认房型和早餐"),
			seed("hotel-booking", "预订酒店", "accommodation", "咨询酒店房型、价格、设施和取消政策"),
			seed("room-issue", "酒店房间问题", "accommodation", "向前台反映房间设施或噪音问题"),
			seed("late-checkout", "延迟退房", "accommodation", "向酒店申请延迟退房并确认时间和费用"),
			seed("hostel-booking", "青年旅舍预订", "accommodation", "询问床位、储物柜、公共设施和入住规则"),
			seed("apartment-viewing", "租房看房", "accommodation", "看出租公寓并询问家具、账单、押金和交通"),
			seed("lease-signing", "签订租约", "accommodation", "与房东核对租期、押金、维修和解约条款"),
			seed("roommate-rules", "室友生活沟通", "accommodation", "和室友商量清洁、访客、噪音和公共空间规则"),
			seed("homestay-arrival", "寄宿家庭入住", "accommodation", "与寄宿家庭确认房间、作息和家庭习惯"),
			seed("home-repair", "预约家中维修", "accommodation", "联系维修人员描述故障并确认上门安排"),

			seed("pharmacy", "药店咨询", "health", "在药店描述轻微症状并询问非处方药"),
			seed("doctor-booking", "预约医生", "health", "联系诊所说明就诊原因并预约时间"),
			seed("doctor-visit", "医生问诊", "health", "向医生描述症状、持续时间和既往病史"),
			seed("dental-visit", "牙科就诊", "health", "描述牙齿不适并确认检查和治疗安排"),
			seed("health-check", "体检结果咨询", "health", "询问体检指标和后续健康建议"),
			seed("emergency-clinic", "急诊接待", "health", "在急诊接待处准确描述紧急症状和用药情况"),
			seed("vaccination", "疫苗接种咨询", "health", "咨询疫苗适用情况、预约和接种后注意事项"),
			seed("eye-exam", "视力检查", "health", "向验光师说明视力变化并了解检查结果"),
			seed("fitness-coach", "健身教练沟通", "health", "说明健身目标、身体状况和训练偏好"),
			seed("mental-wellbeing", "心理咨询预约", "health", "联系咨询机构了解预约、隐私和服务方式"),

			seed("meeting-opinion", "会议表达意见", "workplace", "在团队会议中提出观点并回应同事问题"),
			seed("project-delay", "沟通项目延期", "workplace", "向同事解释延期原因、影响和新计划"),
			seed("deadline", "协商工作期限", "workplace", "与负责人讨论工作量、风险和交付期限"),
			seed("feedback", "绩效反馈沟通", "workplace", "与主管澄清反馈并制定改进计划"),
			seed("salary", "薪资沟通", "workplace", "用工作成果支持薪资调整诉求"),
			seed("cross-team", "跨部门协作", "workplace", "与其他部门确认共同目标、职责分工和协作时间表"),
			seed("client-call", "客户电话", "workplace", "与客户确认需求、时间和下一步安排"),
			seed("presentation", "工作汇报", "workplace", "向团队汇报进展、成果、风险和计划"),
			seed("handover", "工作交接", "workplace", "向同事说明任务状态、资料位置和注意事项"),
			seed("conference-network", "行业会议交流", "workplace", "在行业会议上介绍工作并建立专业联系"),

			seed("new-neighbor", "认识新邻居", "social", "第一次见到邻居并聊聊周边生活"),
			seed("weekend-plan", "商量周末计划", "social", "和朋友比较活动选择并确定时间地点"),
			seed("invite-friend", "邀请朋友参加活动", "social", "介绍活动信息并协调彼此时间"),
			seed("team-lunch", "同事午餐聊天", "social", "和同事聊工作、兴趣和周末安排"),
			seed("language-meetup", "语言交换见面", "social", "与语言伙伴介绍目标、兴趣和练习安排"),
			seed("resolve-conflict", "化解朋友误会", "social", "解释自己的感受并倾听朋友的回应"),
			seed("birthday-party", "参加生日聚会", "social", "在聚会上认识新朋友并参与轻松对话"),
			seed("community-event", "社区活动", "social", "向组织者了解活动内容并与参与者交流"),
			seed("volunteering", "志愿活动报名", "social", "询问志愿任务、时间和需要准备的物品"),
			seed("hobby-club", "兴趣社团交流", "social", "加入兴趣社团并介绍经验和参与期待"),

			seed("teacher-help", "向老师请教", "education", "向老师说明没有理解的知识点并寻求帮助"),
			seed("course-enroll", "课程报名", "education", "咨询课程内容、时间、费用和报名条件"),
			seed("campus-arrival", "校园报到", "education", "询问报到地点、流程和所需材料"),
			seed("library-card", "办理图书证", "education", "了解办证材料、借阅期限和开放时间"),
			seed("group-project", "小组作业分工", "education", "和同学分配任务、确定时间并协调分歧"),
			seed("assignment-extension", "申请作业延期", "education", "向老师说明原因、当前进度和提交计划"),
			seed("office-hours", "参加教授答疑", "education", "在答疑时间讨论课程问题和学习建议"),
			seed("study-plan", "制定学习计划", "education", "和学习顾问讨论目标、时间和课程安排"),
			seed("school-presentation", "课堂展示", "education", "介绍研究主题并回答老师和同学的问题"),
			seed("exam-review", "考试复核", "education", "向老师询问评分标准并讨论答题问题"),

			seed("bank-account", "银行开户", "services", "咨询开户材料、账户费用和办理时间"),
			seed("phone-plan", "办理手机套餐", "services", "比较手机套餐的流量、月费和合约"),
			seed("parcel", "快递问题", "services", "联系快递客服查询包裹并协商处理办法"),
			seed("utility-bill", "水电账单", "services", "联系客服核对账单和计费方式"),
			seed("haircut", "理发需求", "services", "向理发师说明发型、长度和打理偏好"),
			seed("gym-membership", "健身房办卡", "services", "咨询设施、课程、试用和会员合同"),
			seed("insurance", "保险理赔", "services", "说明事故经过并确认保障、材料和流程"),
			seed("internet-install", "安装家庭网络", "services", "咨询网络套餐、安装时间和设备费用"),
			seed("printing-service", "打印店服务", "services", "说明文件格式、纸张、装订和取件时间"),
			seed("lost-and-found", "失物招领", "services", "描述遗失物品、时间和可能遗失的地点"),

			seed("weather-chat", "谈论天气", "other", "围绕近期天气和出行感受展开自然对话"),
			seed("movie-chat", "聊电影", "other", "分享最近看过的电影和观后感"),
			seed("music-chat", "聊音乐", "other", "介绍喜欢的歌手、风格和听歌习惯"),
			seed("travel-memory", "分享旅行经历", "other", "讲述一次旅行经历、难忘细节和感受"),
			seed("daily-routine", "介绍日常作息", "other", "描述工作日和周末的日常安排"),
			seed("city-recommend", "推荐所在城市", "other", "向访客介绍城市景点、美食和交通"),
			seed("technology-life", "讨论科技生活", "other", "交流常用科技产品及其对生活的影响"),
			seed("environment", "讨论环保习惯", "other", "分享节能、回收和绿色出行习惯"),
			seed("future-goals", "谈未来目标", "other", "介绍未来一年的学习、工作和生活目标"),
			seed("cultural-custom", "介绍文化习俗", "other", "介绍一个节日或文化习俗并回答相关问题")
	);

	private static final List<DailyPickTopic> TOPICS = buildTopics();

	static {
		if (TOPICS.size() != 300) {
			throw new IllegalStateException("Daily pick catalog must contain exactly 300 topics");
		}
		if (new HashSet<>(TOPICS.stream().map(DailyPickTopic::id).toList()).size() != TOPICS.size()) {
			throw new IllegalStateException("Daily pick topic ids must be unique");
		}
	}

	public List<DailyPickTopic> topics() {
		return TOPICS;
	}

	private static List<DailyPickTopic> buildTopics() {
		List<DailyPickTopic> topics = new ArrayList<>(SEEDS.size() * VARIATIONS.size());
		for (ScenarioSeed seed : SEEDS) {
			for (Variation variation : VARIATIONS) {
				topics.add(new DailyPickTopic(
						seed.id() + "-" + variation.id(),
						seed.title(),
						seed.category(),
						variation.duration(),
						variation.level(),
						variation.goal(),
						seed.context() + "。" + variation.instruction()));
			}
		}
		return List.copyOf(topics);
	}

	private static ScenarioSeed seed(String id, String title, String category, String context) {
		return new ScenarioSeed(id, title, category, context);
	}

	private record ScenarioSeed(String id, String title, String category, String context) {}

	private record Variation(
			String id,
			String duration,
			String level,
			String goal,
			String instruction) {}
}
