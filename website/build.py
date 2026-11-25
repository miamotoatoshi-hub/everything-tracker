#!/usr/bin/env python3
"""Builds the Trackly website (landing page + Privacy Policy + Terms, English and Russian).

    python3 website/build.py

Reads:
  gradle.properties                    TRACKLY_APPLICATION_ID, DEVELOPER_NAME, SUPPORT_EMAIL, LEGAL_EFFECTIVE_DATE
  app/src/main/res/values*/legal.xml   the same Privacy Policy / Terms the app shows (single source of truth)
  store-assets/raw/                    full-resolution app screenshots (copied into website/assets/screens/)
Writes static HTML next to this script: index.html, privacy.html, terms.html and ru/… .
The pages use no cookies, no analytics, no external fonts or scripts — like the app itself.
Publish the folder with GitHub Pages (.github/workflows/website.yml) or any static host.
"""
import datetime
import html
import os
import re
import shutil
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


# ---------------------------------------------------------------------------------------------- config

def gradle_props():
    props = {}
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


PROPS = gradle_props()
PACKAGE = PROPS.get("TRACKLY_APPLICATION_ID", "com.example.trackly")
PLAY_URL = f"https://play.google.com/store/apps/details?id={PACKAGE}"
DEVELOPER = PROPS.get("DEVELOPER_NAME", "")
EMAIL = PROPS.get("SUPPORT_EMAIL", "")
EFFECTIVE = PROPS.get("LEGAL_EFFECTIVE_DATE", "")

MONTHS = {
    "en": ["January", "February", "March", "April", "May", "June", "July", "August", "September",
           "October", "November", "December"],
    "ru": ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября",
           "октября", "ноября", "декабря"],
}


def long_date(iso, lang):
    try:
        d = datetime.date.fromisoformat(iso)
    except ValueError:
        return iso
    if lang == "ru":
        return f"{d.day} {MONTHS['ru'][d.month - 1]} {d.year} г."
    return f"{MONTHS['en'][d.month - 1]} {d.day}, {d.year}"


# ---------------------------------------------------------------------------------------------- legal

def android_strings(folder):
    path = os.path.join(ROOT, "app", "src", "main", "res", folder, "legal.xml")
    out = {}
    for el in ET.parse(path).getroot():
        text = "".join(el.itertext())
        text = text.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\")
        out[el.get("name")] = text
    return out


def legal_text(lang, key):
    s = android_strings("values-ru" if lang == "ru" else "values")
    developer = DEVELOPER or s["legal_developer_fallback"]
    contact = s["legal_contact_email"].replace("%1$s", EMAIL) if EMAIL else s["legal_contact_fallback"]
    return (s[key].replace("%1$s", developer).replace("%2$s", contact)
            .replace("%3$s", long_date(EFFECTIVE, lang)))


LINK = re.compile(r"https?://[^\s)<]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def linkify(escaped):
    def repl(m):
        value = m.group(0).rstrip(".,")
        tail = m.group(0)[len(value):]
        href = value if "://" in value else "mailto:" + value
        return f'<a href="{href}">{value}</a>{tail}'
    return LINK.sub(repl, escaped)


def legal_html(text):
    parts = []
    for chunk in [c.strip() for c in text.split("\n\n") if c.strip()]:
        lines = chunk.split("\n")
        if lines[0].startswith("# "):
            parts.append(f"<h2>{html.escape(lines[0][2:].strip())}</h2>")
            lines = lines[1:]
        bullets = [l for l in lines if l.startswith("• ")]
        if bullets and len(bullets) == len(lines):
            items = "".join(f"<li>{linkify(html.escape(l[2:]))}</li>" for l in lines)
            parts.append(f"<ul>{items}</ul>")
        elif lines:
            body = "<br>".join(linkify(html.escape(l)) for l in lines)
            parts.append(f"<p>{body}</p>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------------------------- copy

T = {
    "en": {
        "lang": "en", "title": "Trackly — habit tracker for Android",
        "description": "Build habits that stick: streaks, reminders, statistics and a home-screen widget. Private, offline, no ads.",
        "nav_features": "Features", "nav_premium": "Premium", "nav_faq": "FAQ", "other_lang": "Русский",
        "hero_title": "Build habits that stick.",
        "hero_text": "Trackly turns the things you want to do every day into one simple tap — with streaks that keep you going, gentle reminders and statistics that show real progress.",
        "cta": "Get it on Google Play", "cta_note": "Free · No account · No ads",
        "features_title": "Everything a habit needs. Nothing it doesn't.",
        "features": [
            ("✅", "Any habit", "Yes/no habits, counters, minutes, kilometres or pages — with flexible schedules for every day, chosen weekdays or every few days."),
            ("🔥", "Streaks that motivate", "Streaks follow your own schedule, and pausing for a holiday does not break them."),
            ("⏰", "Reminders that help", "Reminders at the times you choose. Mark a habit done right from the notification."),
            ("📊", "Real statistics", "Completion rates, best streaks, a calendar of every day and a weekly overview."),
            ("📱", "Home-screen widget", "Check off today's habits without opening the app."),
            ("🔒", "Private by design", "Works fully offline. Your data stays on your phone — back it up to a file whenever you like."),
        ],
        "shots_title": "Simple on the outside. Thoughtful inside.",
        "premium_title": "Free to start. Premium when you want more.",
        "premium_text": "The free version is complete for everyday use. Premium unlocks everything for people who track more.",
        "free_head": "Free", "premium_head": "Premium",
        "free_items": ["Up to 5 habits", "Up to 2 reminders per habit", "Weekly statistics", "Last 30 days of history", "Backup and restore", "Light and dark theme"],
        "premium_items": ["Unlimited habits and reminders", "Home-screen widget", "Monthly and yearly statistics", "Full history and calendar", "Insights: best day, most consistent habit", "Export to CSV", "Extra colours and themes"],
        "premium_note": "Monthly, yearly (with a free trial for new subscribers) or a one-time lifetime purchase. Prices are shown in your currency in Google Play.",
        "privacy_title": "Your habits are nobody else's business.",
        "privacy_points": ["No account, no sign-up", "No ads, no analytics, no tracking", "Data stored only on your device", "Payments handled by Google Play"],
        "faq_title": "Questions",
        "faq": [
            ("Is Trackly free?", "Yes. You can track up to 5 habits with reminders and weekly statistics for free, forever. Premium is optional."),
            ("Do I need an account?", "No. Trackly works without an account and without the internet."),
            ("What happens to my data if I change phones?", "Use Settings ▸ Your data ▸ Back up to a file, then restore the file on the new phone. Android's own device backup can also bring it over."),
            ("How do I cancel Premium?", "In Google Play: Payments & subscriptions ▸ Subscriptions ▸ Trackly. Premium stays active until the end of the paid period."),
            ("Which languages are supported?", "English, Русский, Español, Deutsch, Français and Português (Brasil)."),
        ],
        "privacy": "Privacy Policy", "terms": "Terms of Use", "contact": "Contact",
        "made_by": "Made by", "home": "Home", "shot_alt": "Trackly app screen",
    },
    "ru": {
        "lang": "ru", "title": "Trackly — трекер привычек для Android",
        "description": "Полезные привычки, которые остаются с вами: серии, напоминания, статистика и виджет. Приватно, офлайн, без рекламы.",
        "nav_features": "Возможности", "nav_premium": "Premium", "nav_faq": "Вопросы", "other_lang": "English",
        "hero_title": "Привычки, которые остаются с вами.",
        "hero_text": "Trackly превращает то, что вы хотите делать каждый день, в одно касание — с сериями, которые мотивируют, бережными напоминаниями и статистикой реального прогресса.",
        "cta": "Скачать в Google Play", "cta_note": "Бесплатно · Без аккаунта · Без рекламы",
        "features_title": "Всё, что нужно привычке. Ничего лишнего.",
        "features": [
            ("✅", "Любые привычки", "«Да/нет», счётчики, минуты, километры или страницы — с гибким расписанием: каждый день, выбранные дни или раз в несколько дней."),
            ("🔥", "Серии, которые мотивируют", "Серии учитывают ваше расписание, а пауза на время отпуска их не сбрасывает."),
            ("⏰", "Напоминания, которые помогают", "Напоминания в выбранное время. Отметить привычку можно прямо из уведомления."),
            ("📊", "Настоящая статистика", "Процент выполнения, лучшие серии, календарь каждого дня и обзор недели."),
            ("📱", "Виджет на главный экран", "Отмечайте привычки на сегодня, не открывая приложение."),
            ("🔒", "Приватность по умолчанию", "Работает полностью офлайн. Данные остаются на телефоне — резервная копия в файл в любой момент."),
        ],
        "shots_title": "Просто снаружи. Продумано внутри.",
        "premium_title": "Бесплатно для старта. Premium — когда хочется большего.",
        "premium_text": "Бесплатная версия полноценна для каждого дня. Premium открывает всё для тех, кто отслеживает больше.",
        "free_head": "Бесплатно", "premium_head": "Premium",
        "free_items": ["До 5 привычек", "До 2 напоминаний на привычку", "Статистика за неделю", "История за 30 дней", "Резервная копия и восстановление", "Светлая и тёмная тема"],
        "premium_items": ["Безлимит привычек и напоминаний", "Виджет на главный экран", "Статистика за месяц и год", "Вся история и календарь", "Инсайты: лучший день, самая стабильная привычка", "Экспорт в CSV", "Дополнительные цвета и темы"],
        "premium_note": "Месяц, год (с бесплатным пробным периодом для новых подписчиков) или разовая покупка навсегда. Цены показываются в вашей валюте в Google Play.",
        "privacy_title": "Ваши привычки — только ваше дело.",
        "privacy_points": ["Без аккаунта и регистрации", "Без рекламы, аналитики и слежки", "Данные только на вашем устройстве", "Оплата — через Google Play"],
        "faq_title": "Вопросы",
        "faq": [
            ("Trackly бесплатный?", "Да. До 5 привычек с напоминаниями и статистикой за неделю — бесплатно и навсегда. Premium — по желанию."),
            ("Нужен ли аккаунт?", "Нет. Trackly работает без аккаунта и без интернета."),
            ("Что будет с данными при смене телефона?", "Настройки ▸ Ваши данные ▸ Резервная копия в файл, затем восстановите файл на новом телефоне. Перенести данные может и резервное копирование Android."),
            ("Как отменить Premium?", "В Google Play: Платежи и подписки ▸ Подписки ▸ Trackly. Premium действует до конца оплаченного периода."),
            ("Какие языки поддерживаются?", "Русский, English, Español, Deutsch, Français и Português (Brasil)."),
        ],
        "privacy": "Политика конфиденциальности", "terms": "Условия использования", "contact": "Связаться",
        "made_by": "Разработчик:", "home": "Главная", "shot_alt": "Экран приложения Trackly",
    },
}

SCREENS = ["01_home", "03_details", "06_calendar", "07_stats", "09_weekly"]

CSS = """
:root{--bg:#f6f6fb;--surface:#fff;--text:#16151f;--muted:#5b5a6e;--line:#e7e6f2;--accent:#5b4ee6;--accent2:#3a2dbf;--chip:#ecebff}
@media (prefers-color-scheme:dark){:root{--bg:#0f0f16;--surface:#1a1a25;--text:#f2f1fa;--muted:#a9a8bd;--line:#2a2a38;--accent:#a49bff;--accent2:#7c7cf8;--chip:#26244a}}
*{box-sizing:border-box}html{scroll-behavior:smooth}
body{margin:0;font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;background:var(--bg);color:var(--text)}
a{color:var(--accent)}img{max-width:100%;height:auto;display:block}
.wrap{max-width:1080px;margin:0 auto;padding:0 20px}
header.top{position:sticky;top:0;z-index:5;backdrop-filter:blur(12px);background:color-mix(in srgb,var(--bg) 80%,transparent);border-bottom:1px solid var(--line)}
header.top .wrap{display:flex;align-items:center;gap:16px;height:60px}
.brand{display:flex;align-items:center;gap:10px;font-weight:700;font-size:19px;color:var(--text);text-decoration:none}
.brand img{width:32px;height:32px;border-radius:8px}
nav{margin-left:auto;display:flex;gap:18px;font-size:15px}nav a{color:var(--muted);text-decoration:none}nav a:hover{color:var(--text)}
.hero{background:linear-gradient(135deg,#7c7cf8,#3a2dbf);color:#fff;overflow:hidden}
.hero .wrap{display:grid;grid-template-columns:1.1fr .9fr;gap:32px;align-items:center;padding-top:64px;padding-bottom:0}
.hero h1{font-size:clamp(34px,5vw,54px);line-height:1.1;margin:0 0 18px;letter-spacing:-.02em}
.hero p{font-size:18px;opacity:.92;margin:0 0 28px;max-width:34em}
.btn{display:inline-flex;align-items:center;gap:10px;background:#fff;color:#2b2190;font-weight:700;padding:14px 22px;border-radius:14px;text-decoration:none;box-shadow:0 10px 30px rgba(0,0,0,.18)}
.btn:hover{transform:translateY(-1px)}
.note{display:block;margin-top:12px;font-size:14px;opacity:.85}
.hero .phones{display:flex;justify-content:center;gap:16px;align-self:end}
.phone{border-radius:28px;background:#14141f;padding:8px;box-shadow:0 20px 50px rgba(0,0,0,.35)}
.phone img{border-radius:22px}
.hero .phone{width:min(260px,40vw);margin-bottom:-60px}
.hero .phone.back{transform:translateY(40px);opacity:.95}
section{padding:72px 0}
h2.section{font-size:clamp(26px,3.4vw,36px);line-height:1.2;margin:0 0 28px;letter-spacing:-.01em;text-align:center}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}
.card{background:var(--surface);border:1px solid var(--line);border-radius:20px;padding:22px}
.card .ico{font-size:28px}.card h3{margin:10px 0 6px;font-size:18px}.card p{margin:0;color:var(--muted)}
.shots{display:flex;gap:18px;overflow-x:auto;padding:8px 4px 20px;scroll-snap-type:x mandatory}
.shots .phone{flex:0 0 auto;width:230px;scroll-snap-align:center}
.plans{display:grid;grid-template-columns:1fr 1fr;gap:18px;max-width:820px;margin:0 auto}
.plan h3{margin:0 0 12px;font-size:20px}.plan ul{margin:0;padding:0;list-style:none}
.plan li{padding:7px 0 7px 28px;position:relative;border-top:1px solid var(--line)}
.plan li:first-child{border-top:0}
.plan li:before{content:"✓";position:absolute;left:4px;color:var(--accent);font-weight:700}
.plan.pro{border:2px solid var(--accent);background:linear-gradient(180deg,var(--chip),var(--surface) 40%)}
.center{text-align:center;color:var(--muted);max-width:44em;margin:0 auto 26px}
.small{font-size:14px;color:var(--muted);text-align:center;margin-top:18px}
.privacy-band{background:var(--surface);border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
.points{display:flex;flex-wrap:wrap;gap:12px;justify-content:center}
.points span{background:var(--chip);padding:10px 16px;border-radius:999px;font-weight:600}
details{background:var(--surface);border:1px solid var(--line);border-radius:16px;padding:16px 20px;margin:0 auto 12px;max-width:760px}
summary{cursor:pointer;font-weight:650}details p{margin:10px 0 0;color:var(--muted)}
footer{padding:36px 0 48px;color:var(--muted);font-size:14px}
footer .wrap{display:flex;flex-wrap:wrap;gap:18px;align-items:center}footer .links{margin-left:auto;display:flex;gap:18px;flex-wrap:wrap}
article.legal{max-width:760px;margin:0 auto;padding:48px 20px 24px}
article.legal h1{font-size:34px;margin:0 0 8px}article.legal h2{font-size:20px;margin:30px 0 8px}
article.legal p,article.legal li{color:var(--muted)}article.legal ul{padding-left:20px}
@media (max-width:820px){.hero .wrap{grid-template-columns:1fr;text-align:center}.hero p{margin-left:auto;margin-right:auto}
.grid{grid-template-columns:1fr 1fr}.plans{grid-template-columns:1fr}nav a:not(.lang){display:none}}
@media (max-width:540px){.grid{grid-template-columns:1fr}.hero .phone.back{display:none}}
"""


def page(lang, prefix, title, description, body):
    t = T[lang]
    other = "../" if lang == "ru" else "ru/"
    canonical_note = ""
    return f"""<!doctype html>
<html lang="{t['lang']}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(description)}">
<meta property="og:image" content="{prefix}assets/feature.png">
<meta name="theme-color" content="#5b4ee6">
<link rel="icon" href="{prefix}assets/favicon-32.png" sizes="32x32">
<link rel="apple-touch-icon" href="{prefix}assets/icon-192.png">
<style>{CSS}</style>{canonical_note}
</head>
<body>
<header class="top"><div class="wrap">
  <a class="brand" href="{prefix}{'ru/' if lang == 'ru' else ''}index.html"><img src="{prefix}assets/icon-192.png" alt="">Trackly</a>
  <nav>
    <a href="{prefix}{'ru/' if lang == 'ru' else ''}index.html#features">{t['nav_features']}</a>
    <a href="{prefix}{'ru/' if lang == 'ru' else ''}index.html#premium">{t['nav_premium']}</a>
    <a href="{prefix}{'ru/' if lang == 'ru' else ''}index.html#faq">{t['nav_faq']}</a>
    <a class="lang" href="{other_page(lang)}" hreflang="{'en' if lang == 'ru' else 'ru'}">{t['other_lang']}</a>
  </nav>
</div></header>
{body}
{footer(lang, prefix)}
</body>
</html>
"""


CURRENT = {"name": "index.html"}


def other_page(lang):
    name = CURRENT["name"]
    return ("../" + name) if lang == "ru" else ("ru/" + name)


def footer(lang, prefix):
    t = T[lang]
    base = prefix + ("ru/" if lang == "ru" else "")
    year = datetime.date.today().year
    who = f"{t['made_by']} {html.escape(DEVELOPER)} · " if DEVELOPER else ""
    contact = f'<a href="mailto:{html.escape(EMAIL)}">{t["contact"]}</a>' if EMAIL else ""
    return f"""<footer><div class="wrap">
  <span>{who}© {year} Trackly</span>
  <span class="links"><a href="{base}privacy.html">{t['privacy']}</a><a href="{base}terms.html">{t['terms']}</a>{contact}</span>
</div></footer>"""


def phone_img(prefix, lang, key, cls=""):
    src = f"{prefix}assets/screens/{lang}_{key}.png"
    return f'<div class="phone {cls}"><img src="{src}" alt="{html.escape(T[lang]['shot_alt'])}" decoding="async"></div>'


def landing(lang):
    t = T[lang]
    prefix = "../" if lang == "ru" else ""
    features = "\n".join(
        f'<div class="card"><div class="ico">{ico}</div><h3>{html.escape(h)}</h3><p>{html.escape(p)}</p></div>'
        for ico, h, p in t["features"]
    )
    shots = "\n".join(phone_img(prefix, lang, k) for k in SCREENS)
    free = "".join(f"<li>{html.escape(i)}</li>" for i in t["free_items"])
    pro = "".join(f"<li>{html.escape(i)}</li>" for i in t["premium_items"])
    points = "".join(f"<span>{html.escape(p)}</span>" for p in t["privacy_points"])
    faq = "\n".join(
        f"<details><summary>{html.escape(q)}</summary><p>{html.escape(a)}</p></details>" for q, a in t["faq"]
    )
    body = f"""
<section class="hero"><div class="wrap">
  <div>
    <h1>{html.escape(t['hero_title'])}</h1>
    <p>{html.escape(t['hero_text'])}</p>
    <a class="btn" href="{PLAY_URL}">▶ {html.escape(t['cta'])}</a>
    <span class="note">{html.escape(t['cta_note'])}</span>
  </div>
  <div class="phones">{phone_img(prefix, lang, '07_stats', 'back')}{phone_img(prefix, lang, '01_home')}</div>
</div></section>

<section id="features"><div class="wrap">
  <h2 class="section">{html.escape(t['features_title'])}</h2>
  <div class="grid">{features}</div>
</div></section>

<section><div class="wrap">
  <h2 class="section">{html.escape(t['shots_title'])}</h2>
  <div class="shots">{shots}</div>
</div></section>

<section id="premium"><div class="wrap">
  <h2 class="section">{html.escape(t['premium_title'])}</h2>
  <p class="center">{html.escape(t['premium_text'])}</p>
  <div class="plans">
    <div class="card plan"><h3>{t['free_head']}</h3><ul>{free}</ul></div>
    <div class="card plan pro"><h3>✨ {t['premium_head']}</h3><ul>{pro}</ul></div>
  </div>
  <p class="small">{html.escape(t['premium_note'])}</p>
</div></section>

<section class="privacy-band"><div class="wrap">
  <h2 class="section">{html.escape(t['privacy_title'])}</h2>
  <div class="points">{points}</div>
</div></section>

<section id="faq"><div class="wrap">
  <h2 class="section">{html.escape(t['faq_title'])}</h2>
  {faq}
</div></section>
"""
    return page(lang, prefix, t["title"], t["description"], body)


def legal_page(lang, key, title):
    prefix = "../" if lang == "ru" else ""
    body = f'<article class="legal"><h1>{html.escape(title)}</h1>\n{legal_html(legal_text(lang, key))}\n</article>'
    return page(lang, prefix, f"{title} — Trackly", title, body)


def write(rel, content):
    path = os.path.join(HERE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def copy_screens():
    raw = os.path.join(ROOT, "store-assets", "raw")
    out = os.path.join(HERE, "assets", "screens")
    os.makedirs(out, exist_ok=True)
    for lang in ("en", "ru"):
        for key in SCREENS:
            src = os.path.join(raw, f"store_{lang}_light_{key}.png")
            if os.path.exists(src):
                shutil.copyfile(src, os.path.join(out, f"{lang}_{key}.png"))
    feature = os.path.join(ROOT, "store-assets", "feature_graphic_1024x500.png")
    if os.path.exists(feature):
        shutil.copyfile(feature, os.path.join(HERE, "assets", "feature.png"))


def main():
    copy_screens()
    for lang in ("en", "ru"):
        base = "ru/" if lang == "ru" else ""
        t = T[lang]
        CURRENT["name"] = "index.html"
        write(base + "index.html", landing(lang))
        CURRENT["name"] = "privacy.html"
        write(base + "privacy.html", legal_page(lang, "legal_privacy_body", t["privacy"]))
        CURRENT["name"] = "terms.html"
        write(base + "terms.html", legal_page(lang, "legal_terms_body", t["terms"]))
    print("website built:", HERE)


if __name__ == "__main__":
    main()
