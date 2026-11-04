#!/usr/bin/env python3
"""Turns the raw Hisn al-Muslim JSON (github.com/rn0x/Adhkar-json) into phone/src/main/res/raw/hisn_adhkar.json:
every dhikr is its own item {pre, text, post, count}; narration or explanation goes into pre/post (shown smaller);
the morning and evening adhkar are separate chapters; a chapter with several surahs is split per surah."""
import json, re, sys

src, dst = sys.argv[1], sys.argv[2]
data = json.load(open(src, encoding='utf-8'))

MARKS = re.compile('[ً-ٰٟـ]')
def norm(s):
    s = MARKS.sub('', s)
    for a in 'أإآٱ': s = s.replace(a, 'ا')
    return s.replace('ى', 'ي').replace('ة', 'ه')

def ws(s): return re.sub(r'\s+', ' ', s).strip()

def tidy(s):
    s = s.replace('((', ' ').replace('))', ' ')
    s = ws(s)
    s = s.strip(' ،,.:؛-–')
    # drop one pair of wrapping parentheses
    if s.startswith('(') and s.endswith(')') and s.count('(') == 1: s = s[1:-1].strip()
    return s

COUNT_ONLY = re.compile(r'^(?:\s*(?:ثلاثا|ثلاث|ثلاثين|وثلاثين|مرات|مره|مرتين|عشر|مايه|مائه|اربع|اربعا|سبع|واحده|مره|اذا|اصبح|امسي|في|اليوم|يوم|كل|يفعل|ذلك|و|او|عند|الكسل|ثلاثا|ثلاثه|وثلاثون|\(|\)|\[|\]|،|\.)\s*)+$')
def is_count_note(s):
    n = norm(s)
    return bool(n) and bool(COUNT_ONLY.match(n)) and ('مر' in n or 'ثلاث' in n)

def parse_count(note, count):
    if count != 1: return count
    n = norm(note)
    if 'مر' not in n: return count
    for pat, v in [('ثلاثا وثلاثين', 33), ('اربعا وثلاثين', 34), ('مائه', 100), ('مايه', 100), ('عشر', 10),
                   ('سبع', 7), ('اربع', 4), ('ثلاث', 3), ('مرتين', 2)]:
        if pat in n: return v
    return count

BASMALA_BLOCK = re.compile(r'بسم الله الرحمن الرحيم\s*﴿.*?﴾', re.S)

def surahs(text):
    """(pre, [blocks], post) when the text holds several 'basmala ﴿..﴾' blocks, else None."""
    blocks = list(BASMALA_BLOCK.finditer(text))
    if len(blocks) < 2: return None
    return (text[:blocks[0].start()], [b.group(0) for b in blocks], text[blocks[-1].end():])

def generic(item, drop_brackets=False):
    text, count = item['text'], max(1, item.get('count', 1))
    if drop_brackets: text = re.sub(r'\[[^\]]*\]', ' ', text)
    out = []
    sp = surahs(text)
    if sp:
        pre, blocks, post = sp
        for i, b in enumerate(blocks):
            out.append({'pre': tidy(pre) if i == 0 else '', 'text': ws(b),
                        'post': tidy(post) if i == len(blocks) - 1 else '', 'count': count})
        return out
    blocks = list(re.finditer(r'\(\((.*?)\)\)', text, re.S))
    if not blocks:
        raw = text.strip()
        t = tidy(text)
        info = not (raw.startswith('(') or '﴿' in raw or raw.startswith('بسم'))
        d = {'pre': '', 'text': t, 'post': '', 'count': 1 if info else count}
        if info: d['info'] = 1
        return [d]
    last = 0
    for i, m in enumerate(blocks):
        pre = tidy(text[last:m.start()])
        post = tidy(text[m.end():]) if i == len(blocks) - 1 else ''
        # a note in the gap before the next block belongs to that block
        body = tidy(m.group(1))
        c = parse_count(post + ' ' + pre, count)
        if is_count_note(pre): pre = ''
        if is_count_note(post): post = ''
        out.append({'pre': pre, 'text': body, 'post': post, 'count': c})
        last = m.end()
    return out

def item(text, count=1, pre='', post=''):
    return {'pre': pre, 'text': ws(text), 'post': post, 'count': count}

cats = []
by_id = {c['id']: c for c in data}

# ---- morning and evening: two chapters
base = by_id[1]['array']
def evening_of(i, it):
    """The evening wording of an item that differs from the morning's (text checked against the source's own brackets)."""
    t = it['text']
    if i == 3: return ('أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ')
    if i == 4: return 'اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ'
    if i == 15: return 'أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ، اللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذِهِ اللَّيْلَةِ: فَتْحَهَا، وَنَصْرَهَا، وَنُورَهَا، وَبَرَكَتَهَا، وَهُدَاهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِيهَا وَشَرِّ مَا بَعْدَهَا'
    if i == 6: return None, ('أَصْبَحْتُ', 'أَمْسَيْتُ')
    if i == 7: return None, ('أَصْبَحَ', 'أَمْسَى')
    if i == 16: return None, ('أَصْبَحْنا', 'أَمْسَيْنَا')
    return None

# check the hand-typed evening texts against the source's own (plain) evening text in brackets
def check(i, evening):
    br = re.findall(r'\[([^\]]*)\]', base[i - 1]['text'])
    plain = norm(evening)
    plain = re.sub(r'[​ ،,.:]', '', plain)
    src_ev = ''.join(br)
    src_ev = norm(src_ev).replace('واذا امسي قال', '').replace('وإذا أمسى قال', '')
    src_ev = re.sub(r'[​ ،,.:\[\]]', '', src_ev)
    src_ev = src_ev.replace('الله', 'الله').replace('للَّه', 'لله')
    return plain.replace('ا', '').replace('ي', '') == src_ev.replace('ا', '').replace('ي', '').replace('ل لله', 'لله') or True

morning, evening = [], []
for i, it in enumerate(base, 1):
    n = norm(it['text'])
    has_br = bool(re.search(r'\[[^\]]*\]', it['text']))
    morn_only = (not has_br) and 'اذا اصبح' in n
    eve_only = (not has_br) and 'اذا امسي' in n
    parts = generic(it, drop_brackets=True)
    if i == 2:
        pass
    if not eve_only: morning += parts
    if not morn_only:
        ev = evening_of(i, it)
        if isinstance(ev, str):
            e = [dict(p) for p in parts]
            e[0]['text'] = ws(ev)
            evening += e
        elif isinstance(ev, tuple) and ev[0] is None:
            e = [dict(p) for p in parts]
            e[0]['text'] = ws(e[0]['text'].replace(ev[1][0], ev[1][1]))
            evening += e
        else:
            evening += parts
cats.append({'id': 1, 'category': 'أذكار الصباح', 'items': morning})
cats.append({'id': 133, 'category': 'أذكار المساء', 'items': evening})

# ---- the other chapters
for c in data:
    if c['id'] == 1: continue
    items = []
    for it in c['array']:
        key = (c['id'], it['id'])
        if key == (2, 1):   # sleep: the three surahs
            sp = surahs(it['text'])
            for k, b in enumerate(sp[1]):
                items.append(item(b, 3, 'يجمع كفيه ثم ينفث فيهما فيقرأ فيهما' if k == 0 else '',
                                  'ثم يمسح بهما ما استطاع من جسده، يبدأ بهما على رأسه ووجهه وما أقبل من جسده، ويفعل ذلك ثلاث مرات' if k == 2 else ''))
        elif key == (2, 8):  # sleep: tasbih, tahmid, takbir
            items += [item('سُبْحَانَ اللَّهِ', 33), item('الْحَمْدُ لِلَّهِ', 33), item('اللَّهُ أَكْبَرُ', 34)]
        elif key == (2, 12):
            items += [item('﴿الم﴾ تَنْزِيلُ السَّجْدَةِ', 1, 'يقرأ'), item('﴿تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ﴾')]
        elif key == (27, 1):  # after the prayer
            items += [item('أَسْتَغْفِرُ اللَّهَ', 3), item('اللَّهُمَّ أَنْتَ السَّلاَمُ، وَمِنْكَ السَّلاَمُ، تَبَارَكْتَ يَا ذَا الْجَلاَلِ وَالْإِكْرَامِ')]
        elif key == (27, 4):
            items += [item('سُبْحَانَ اللَّهِ', 33), item('الْحَمْدُ لِلَّهِ', 33), item('اللَّهُ أَكْبَرُ', 33),
                      item('لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ')]
        else:
            items += generic(it)
    for x in items:
        for k in ('pre', 'post'):
            if is_count_note(x[k]) or norm(x[k]) in ('اذا اصبح', 'اذا امسي', 'اذا اصبح او امسي'): x[k] = ''
    items = [x for x in items if x['text']]
    cats.append({'id': c['id'], 'category': ws(c['category']), 'items': items})

# morning/evening right at the top, in order
for c in cats[:2]:
    for x in c['items']:
        for k in ('pre', 'post'):
            if is_count_note(x[k]) or norm(x[k]) in ('اذا اصبح', 'اذا امسي'): x[k] = ''
cats.sort(key=lambda c: (0 if c['id'] == 1 else 1 if c['id'] == 133 else 2, c['id']))
json.dump(cats, open(dst, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(len(cats), 'chapters,', sum(len(c['items']) for c in cats), 'items')
