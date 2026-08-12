import { useEffect, useState } from "react";
import {
  ArrowRight,
  ArrowUpRight,
  BookOpenText,
  Briefcase,
  CaretDown,
  Check,
  CheckCircle,
  ChatCircleDots,
  Clock,
  DeviceMobile,
  List,
  Microphone,
  PhoneDisconnect,
  ShieldCheck,
  Sparkle,
  Subtitles,
  Translate,
  X,
} from "@phosphor-icons/react";
import { plans, teachers } from "../../domain/content/data.js";
import "./landing.css";

const navItems = [
  { label: "产品能力", href: "#capabilities" },
  { label: "使用方式", href: "#how-it-works" },
  { label: "AI 老师", href: "#teachers" },
  { label: "会员方案", href: "#pricing" },
];

const capabilities = [
  {
    icon: Microphone,
    number: "01",
    title: "实时语音对话",
    description: "像和朋友聊天一样自然开口，支持字幕、翻译和语速调节。",
    tag: "REAL-TIME",
    tone: "landing-tone-blue",
  },
  {
    icon: BookOpenText,
    number: "02",
    title: "场景化学习",
    description: "从点单、出行到职场沟通，把每一次对话拆成可复习的表达。",
    tag: "SCENARIO PRACTICE",
    tone: "landing-tone-yellow",
  },
  {
    icon: ChatCircleDots,
    number: "03",
    title: "说完就有反馈",
    description: "逐轮记录表达表现，生成学习资产，知道下一次该练什么。",
    tag: "SMART FEEDBACK",
    tone: "landing-tone-green",
  },
  {
    icon: Briefcase,
    number: "04",
    title: "专项训练",
    description: "IELTS 口语与英文面试，使用更贴近真实目标的训练流程。",
    tag: "GOAL-BASED",
    tone: "landing-tone-coral",
  },
];

const workflow = [
  { number: "01", title: "选择你的 AI 老师", description: "按口音和陪练风格，找到适合自己的对话伙伴。" },
  { number: "02", title: "从一个场景开始说", description: "不用写稿，跟着真实情境自然回应，随时暂停或重来。" },
  { number: "03", title: "复盘下一次怎么说", description: "查看逐轮反馈和学习资产，把今天说过的变成自己的表达。" },
];

const faqs = [
  { question: "需要准备教材或写好稿子吗？", answer: "不需要。你可以直接选择一个场景开始，AI 老师会根据你的水平和回应自然推进对话。" },
  { question: "免费版可以练习什么？", answer: "免费版包含每日自由对话额度、每日普通场景和全部 AI 老师，适合先建立稳定开口的习惯。" },
  { question: "移动端什么时候可以下载？", answer: "移动端正在准备正式发布。商店链接配置完成后，这里会自动显示 iOS 和 Android 下载入口。" },
  { question: "AI 的反馈可以替代老师吗？", answer: "UniSpeaking 用于日常练习和即时反馈，不替代专业教师、考试官方评分或正式语言诊断。" },
];

function Brand() {
  return (
    <a className="landing-brand" href="#top" aria-label="返回 UniSpeaking 首页">
      <span className="landing-brand__mark"><img src="/brand/unispeaking-mark-user.jpg" alt="" /></span>
      <img className="landing-brand__wordmark" src="/brand/unispeaking-wordmark.png" alt="UniSpeaking" />
    </a>
  );
}

function LandingButton({ children, variant = "primary", icon, onClick, href, ...props }) {
  const className = `landing-button landing-button--${variant}`;
  if (href) {
    return <a className={className} href={href} onClick={onClick} {...props}>{children}{icon}</a>;
  }
  return <button className={className} type="button" onClick={onClick} {...props}>{children}{icon}</button>;
}

function ConversationPreview({ teacher }) {
  const waveform = [.32, .56, .82, .48, .7, .92, .58, .38, .68, .44];
  return (
    <div className="landing-call-preview" aria-label="UniSpeaking 真实对话界面预览">
      <div className="landing-call-preview__presence">
        <div className="landing-call-preview__avatar">
          <img src={teacher.image} alt={`${teacher.name} AI 老师`} />
        </div>
        <div className="landing-call-preview__wave" aria-hidden="true">
          {waveform.map((height, index) => <i key={index} style={{ "--wave-height": height }} />)}
        </div>
        <time>00:27</time>
      </div>
      <div className="landing-call-preview__transcript">
        <article>
          <small>{teacher.name}</small>
          <p>What would you like to practice today?</p>
          <span><Translate />翻译</span>
        </article>
        <article className="is-user">
          <small>你</small>
          <p>I have an interview next week. Can we practice?</p>
          <span><Translate />翻译</span>
        </article>
        <article>
          <small>{teacher.name}</small>
          <p>Of course. Let&apos;s start with a confident introduction.</p>
          <span><Translate />翻译</span>
        </article>
      </div>
      <div className="landing-call-preview__controls" aria-hidden="true">
        <span><Microphone /></span>
        <span className="is-active"><Subtitles /></span>
        <span className="is-end"><PhoneDisconnect weight="fill" /></span>
      </div>
    </div>
  );
}

function OpeningHero({ onStart }) {
  return (
    <section className="landing-opening" aria-labelledby="landing-opening-title">
      <div className="landing-opening__grid" aria-hidden="true"><span /><span /><span /><span /></div>
      <div className="landing-container landing-opening__inner">
        <div className="landing-opening__copy">
          <p className="landing-opening__eyebrow">UNISPEAKING · AI ENGLISH SPEAKING</p>
          <h1 id="landing-opening-title">
            <span className="landing-opening__title-line">越说</span>
            <span className="landing-opening__title-line">越会说</span>
            <small>Speak more,<br />Speak better.</small>
          </h1>
          <p className="landing-opening__lead">每天多说一句，把想表达的，慢慢变成自然的表达。</p>
          <div className="landing-opening__actions">
            <LandingButton onClick={onStart} icon={<ArrowRight weight="bold" />}>开始练习</LandingButton>
            <a href="#product" className="landing-opening__explore">向下探索 <span aria-hidden="true">↓</span></a>
          </div>
        </div>
        <div className="landing-opening__scroll" aria-hidden="true"><span>SCROLL TO EXPLORE</span><i /></div>
      </div>
    </section>
  );
}

function CapabilityCard({ capability: item }) {
  const Icon = item.icon;
  return (
    <article className={`landing-capability-card ${item.tone}`}>
      <div className="landing-capability-card__top"><span>{item.number}</span><Icon weight="duotone" /></div>
      <p className="landing-eyebrow">{item.tag}</p>
      <h3>{item.title}</h3>
      <p>{item.description}</p>
      <span className="landing-capability-card__line" aria-hidden="true" />
    </article>
  );
}

export function LandingPage({ onStart, onLogin, onWeb, onSpecialty }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [activeTeacherId, setActiveTeacherId] = useState(teachers[0]?.id || "clara");
  const [openFaq, setOpenFaq] = useState(0);
  const activeTeacher = teachers.find((teacher) => teacher.id === activeTeacherId) || teachers[0];
  const heroTeacher = teachers.find((teacher) => teacher.id === "clara") || teachers[0];

  useEffect(() => {
    document.body.classList.toggle("landing-menu-open", menuOpen);
    return () => document.body.classList.remove("landing-menu-open");
  }, [menuOpen]);

  useEffect(() => {
    const root = document.querySelector(".landing-page");
    if (!root) return undefined;
    root.classList.add("landing-motion-ready");
    const targets = [...root.querySelectorAll("[data-reveal]")];
    if (!("IntersectionObserver" in window)) {
      targets.forEach((target) => target.classList.add("is-visible"));
      return () => root.classList.remove("landing-motion-ready");
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -8%" });
    targets.forEach((target) => observer.observe(target));
    return () => {
      observer.disconnect();
      root.classList.remove("landing-motion-ready");
    };
  }, []);

  const closeMenu = () => setMenuOpen(false);

  return (
    <main className="landing-page" id="top">
      <header className={`landing-header ${menuOpen ? "is-menu-open" : ""}`}>
        <div className="landing-container landing-header__inner">
          <Brand />
          <nav className="landing-nav" aria-label="主导航">
            {navItems.map((item) => <a key={item.href} href={item.href}>{item.label}</a>)}
          </nav>
          <div className="landing-header__actions">
            <button className="landing-header__login" type="button" onClick={onLogin}>登录</button>
            <LandingButton onClick={onStart} icon={<ArrowRight weight="bold" />}>开始练习</LandingButton>
          </div>
          <button className="landing-menu-toggle" type="button" aria-label={menuOpen ? "关闭菜单" : "打开菜单"} aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
            {menuOpen ? <X weight="bold" /> : <List weight="bold" />}
          </button>
        </div>
        {menuOpen && <div className="landing-mobile-menu">
          <nav aria-label="移动端主导航">
            {navItems.map((item) => <a key={item.href} href={item.href} onClick={closeMenu}>{item.label}<ArrowUpRight weight="bold" /></a>)}
          </nav>
          <div className="landing-mobile-menu__actions"><button type="button" onClick={() => { closeMenu(); onLogin(); }}>登录</button><LandingButton onClick={() => { closeMenu(); onStart(); }} icon={<ArrowRight weight="bold" />}>开始练习</LandingButton></div>
        </div>}
      </header>

      <OpeningHero onStart={onStart} />

      <section className="landing-hero" id="product" data-reveal>
        <div className="landing-hero__borders" aria-hidden="true"><span /><span /></div>
        <div className="landing-container landing-hero__grid">
          <div className="landing-hero__copy">
            <a className="landing-announcement" href="#capabilities"><Sparkle weight="fill" /><span>AI 实时口语陪练，今天就能开始</span><ArrowRight weight="bold" /></a>
            <p className="landing-eyebrow">AI ENGLISH SPEAKING PARTNER</p>
            <h1>不用等准备好，<br /><em>先开口。</em></h1>
            <p className="landing-hero__lead">和懂你的 AI 老师自然对话，在低压力的练习里，把“想说”慢慢变成“会说”。</p>
            <div className="landing-hero__actions">
              <LandingButton onClick={onStart} icon={<ArrowRight weight="bold" />}>免费开始练习</LandingButton>
              <LandingButton variant="secondary" onClick={onWeb} icon={<ArrowUpRight weight="bold" />}>进入 Web</LandingButton>
            </div>
            <div className="landing-hero__trust"><span><Check weight="bold" />无需准备教材</span><span><Check weight="bold" />每日都有免费额度</span><span><Check weight="bold" />随时暂停或重来</span></div>
          </div>
          <div className="landing-hero__visual"><ConversationPreview teacher={heroTeacher} /></div>
        </div>
      </section>

      <section className="landing-section landing-capabilities" id="capabilities" data-reveal>
        <div className="landing-container">
          <div className="landing-section-heading"><div><p className="landing-eyebrow">ONE PRACTICE, MORE THAN WORDS</p><h2>不是背答案，<br />是把表达练成习惯。</h2></div><p>UniSpeaking 把对话、场景、反馈和复习放进同一条练习路径，让你知道怎么开始，也知道下一步怎么进步。</p></div>
          <div className="landing-capabilities-grid">{capabilities.map((item) => <CapabilityCard key={item.number} capability={item} />)}</div>
        </div>
      </section>

      <section className="landing-section landing-workflow" id="how-it-works" data-reveal>
        <div className="landing-container landing-workflow__grid"><div className="landing-workflow__intro"><p className="landing-eyebrow">A SIMPLE LOOP</p><h2>三步，<br />让开口变简单。</h2><p>从一句不完美的回答开始。AI 老师不会打断你，而是帮助你把对话继续下去。</p><LandingButton variant="secondary" onClick={onStart} icon={<ArrowRight weight="bold" />}>开始你的第一次对话</LandingButton></div><div className="landing-steps">{workflow.map((item, index) => <article key={item.number} className="landing-step"><span className="landing-step__number">{item.number}</span><div><h3>{item.title}</h3><p>{item.description}</p></div>{index < workflow.length - 1 && <span className="landing-step__connector" aria-hidden="true" />}</article>)}</div></div>
      </section>

      <section className="landing-section landing-teachers" id="teachers" data-reveal>
      <div className="landing-container"><div className="landing-section-heading landing-section-heading--compact"><div><p className="landing-eyebrow">MEET YOUR AI PARTNERS</p><h2>找到让你愿意<br />一直说下去的人。</h2></div><p>六位 AI 老师，各自拥有固定的口音和陪练方式。先试听，再选择今天想和谁练习。</p></div><div className="landing-teacher-layout"><div className="landing-teacher-list" aria-label="AI 老师列表">{teachers.map((teacher) => <button key={teacher.id} type="button" className={`landing-teacher-card ${teacher.id === activeTeacher.id ? "is-active" : ""}`} onClick={() => setActiveTeacherId(teacher.id)}><img src={teacher.image} alt={teacher.name} /><span><strong>{teacher.name}</strong><small>{teacher.accent} · {teacher.personality}</small></span><ArrowRight weight="bold" /></button>)}</div><article className="landing-teacher-feature"><div className="landing-teacher-feature__image"><img src={activeTeacher.image} alt={`${activeTeacher.name} AI 老师`} /></div><div className="landing-teacher-feature__copy"><p className="landing-eyebrow">YOUR NEXT CONVERSATION</p><h3>{activeTeacher.name}</h3><p>{activeTeacher.method}</p><blockquote>“{activeTeacher.intro}”</blockquote><audio controls preload="none" src={activeTeacher.previewAudio} aria-label={`试听 ${activeTeacher.name} 的声音`} /><LandingButton onClick={() => onStart(activeTeacher.voiceId)} icon={<ArrowRight weight="bold" />}>和 {activeTeacher.name} 开始练习</LandingButton></div></article></div></div>
      </section>

      <section className="landing-section landing-specialties" data-reveal>
        <div className="landing-container"><div className="landing-section-heading landing-section-heading--compact"><div><p className="landing-eyebrow">PRACTICE FOR REAL LIFE</p><h2>你想说的，<br />都可以成为练习。</h2></div><p>从日常交流到重要目标，选择一个真实场景，马上进入更有方向的对话。</p></div><div className="landing-specialty-grid"><article><div className="landing-specialty-card__icon landing-tone-yellow"><BookOpenText weight="duotone" /></div><p className="landing-eyebrow">EVERYDAY ENGLISH</p><h3>日常场景</h3><p>点单、购物、出行、住宿和社交，让下次真实交流少一点犹豫。</p><a href="#capabilities">查看场景 <ArrowRight weight="bold" /></a></article><article><div className="landing-specialty-card__icon landing-tone-lavender"><TargetIcon /></div><p className="landing-eyebrow">IELTS SPEAKING</p><h3>IELTS 口语</h3><p>Part 1、Part 2、Part 3，从回答结构到表达表现，完整练习每一部分。</p><button type="button" aria-label="开始体验 IELTS 口语" onClick={() => onSpecialty("ielts")}>开始体验 <ArrowRight weight="bold" /></button></article><article><div className="landing-specialty-card__icon landing-tone-sky"><Briefcase weight="duotone" /></div><p className="landing-eyebrow">INTERVIEW PRACTICE</p><h3>英文面试</h3><p>用更接近真实面试的方式，练习自我介绍、追问和清晰表达。</p><button type="button" aria-label="开始体验英文面试" onClick={() => onSpecialty("interview")}>开始体验 <ArrowRight weight="bold" /></button></article></div></div>
      </section>

      <section className="landing-platform" id="platforms" data-reveal><div className="landing-container landing-platform__grid"><div className="landing-platform__copy"><p className="landing-eyebrow">PRACTICE WHEREVER YOU ARE</p><h2>电脑前深入练，<br />手机上随时说。</h2><p>Web 端适合完整训练和复盘，移动端适合把零碎时间变成开口时间。你的练习路径，会跟着你走。</p><div className="landing-platform__actions"><LandingButton onClick={onWeb} icon={<ArrowUpRight weight="bold" />}>进入 Web 端</LandingButton><span className="landing-platform__note"><CheckCircle weight="fill" />无需安装，即开即用</span></div></div><div className="landing-devices"><div className="landing-device landing-device--desktop"><div className="landing-device__bar"><i /><i /><i /><span>app.unispeaking.com</span></div><div className="landing-device__screen"><div className="landing-mini-sidebar"><span /><span /><span /><span /></div><div className="landing-mini-main"><small>自由对话</small><strong>今天想聊点什么？</strong><div className="landing-mini-wave">{[.25, .5, .8, .43, .68, .32, .58, .92, .44, .7, .35, .62].map((height, index) => <i key={index} style={{ "--wave-height": height }} />)}</div><span className="landing-mini-button">开始对话 <ArrowRight weight="bold" /></span></div></div></div><div className="landing-device landing-device--mobile"><div className="landing-device__speaker" /><div className="landing-device__screen"><img src="/brand/unispeaking-mark-user.jpg" alt="" /><strong>移动端即将上线</strong><span>把练习放进口袋</span><i><DeviceMobile weight="duotone" /></i></div></div><div className="landing-devices__badge"><DeviceMobile weight="duotone" /><span><strong>Mobile app</strong><small>Coming soon</small></span></div></div></div></section>

      <section className="landing-section landing-pricing" id="pricing" data-reveal><div className="landing-container"><div className="landing-section-heading landing-section-heading--compact"><div><p className="landing-eyebrow">MEMBERSHIP PREVIEW</p><h2>先免费开始，<br />再按你的节奏升级。</h2></div><p>免费版足够你建立习惯。需要更长时间、更完整的专项训练时，再选择适合自己的方案。</p></div><div className="landing-pricing-grid">{plans.map((plan) => <article key={plan.id} className={`landing-plan ${plan.id === "pro" ? "is-featured" : ""}`}><div className="landing-plan__heading"><div><span className="landing-plan__label">{plan.id === "pro" ? "最受欢迎" : plan.id === "free" ? "从这里开始" : "目标训练"}</span><h3>{plan.name}</h3></div>{plan.id === "pro" && <span className="landing-plan__badge">推荐</span>}</div><p>{plan.desc}</p><div className="landing-plan__price"><strong>{plan.price === "0" ? "免费" : `¥${plan.price}`}</strong>{plan.suffix && <span>{plan.suffix}</span>}</div><ul>{plan.features.map((feature) => <li key={feature}><CheckCircle weight="fill" />{feature}</li>)}</ul><LandingButton variant={plan.id === "pro" ? "primary" : "secondary"} onClick={onStart} icon={<ArrowRight weight="bold" />}>{plan.id === "free" ? "免费开始" : "加入候补"}</LandingButton></article>)}</div><p className="landing-pricing__footnote"><ShieldCheck weight="bold" /> 会员支付正在准备中，正式权益和价格以发布版本为准。</p></div></section>

      <section className="landing-section landing-faq" data-reveal><div className="landing-container landing-faq__grid"><div><p className="landing-eyebrow">QUESTIONS, ANSWERED</p><h2>开始之前，<br />你可能想知道。</h2><p>还有其他问题？可以先访问帮助中心，或登录后从产品内联系我们。</p><a href="/help">访问帮助中心 <ArrowUpRight weight="bold" /></a></div><div className="landing-faq__list">{faqs.map((faq, index) => <article key={faq.question} className={openFaq === index ? "is-open" : ""}><button type="button" aria-expanded={openFaq === index} onClick={() => setOpenFaq(openFaq === index ? -1 : index)}><span>{faq.question}</span><CaretDown weight="bold" /></button>{openFaq === index && <p>{faq.answer}</p>}</article>)}</div></div></section>

      <section className="landing-final-cta" data-reveal><div className="landing-container landing-final-cta__inner"><div><p className="landing-eyebrow">YOUR NEXT SENTENCE STARTS HERE</p><h2>今天，先说一句。</h2><p>不用等状态完美。打开 UniSpeaking，让一次自然的对话成为下一次进步的起点。</p></div><LandingButton variant="light" onClick={onStart} icon={<ArrowRight weight="bold" />}>免费开始练习</LandingButton></div></section>

      <footer className="landing-footer"><div className="landing-container"><div className="landing-footer__top"><Brand /><p>让每一次开口，都有回应。</p><div className="landing-footer__links"><a href="#capabilities">产品能力</a><a href="#pricing">会员方案</a><a href="/about">关于产品</a><a href="/help">帮助中心</a></div></div><div className="landing-footer__bottom"><span>© 2026 UniSpeaking · 语你说</span><span><a href="/about/privacy-policy">隐私政策</a><a href="/about/user-agreement">用户协议</a></span></div></div></footer>
    </main>
  );
}

function TargetIcon() {
  return <span className="landing-target-icon" aria-hidden="true"><i /><i /><i /></span>;
}
