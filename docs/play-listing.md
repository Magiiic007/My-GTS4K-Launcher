# Google Play: store listing, permission declarations and Data safety

## App details
- Name (30 chars): الصلاة خير (Salat Khair)
- Short description (80 chars), Arabic: مواقيت الصلاة والأذان والأذكار والراديو وتتبّع الصلوات، بدون إعلانات.
- Short description, English: Prayer times, adhan, adhkar, radio and prayer tracking. No ads.
- Category: Lifestyle (or Books & Reference). Free, no ads, no in-app purchases.
- Privacy policy URL: the GitHub Pages address of docs/privacy.html

## Full description (Arabic)
«الصلاة خير» رفيقك اليومي للصلاة والذكر، بدون إعلانات ولا تسجيل دخول.

• مواقيت الصلاة بدقة لمدينتك مع اختيار طريقة الحساب والمذهب وتعديل الدقائق، وتحديد المدينة تلقائيًا أو يدويًا.
• أذان في وقته وإقامة، مع اختيار لكل صلاة: صوت أو اهتزاز أو إشعار فقط، وقلب الهاتف لإيقاف الأذان.
• كتم الهاتف وقت الصلاة ثم إعادته بعد الإقامة تلقائيًا.
• حصن المسلم كاملًا: أذكار وأدعية مجمّعة في أبواب مرتّبة، مع بحث عن ذكر أو دعاء، وعدّاد لكل ذكر، وإمكانية إضافة أذكارك ونقل الأبواب بين المجموعات.
• أذكار الصباح والمساء وأذكار ما بعد الصلاة، مفصولة بين الفجر والمغرب وباقي الصلوات.
• أيقونة عائمة تذكّرك بالأذكار في أوقاتها، وبطاقات أذكار خفيفة فوق التطبيقات (اختيارية).
• دعاء ما بعد الأذان، وتذكير «أقم الصلاة» بالصلوات التي لم تعلّمها.
• متابعة الصلوات اليومية مع قضاء ما فاتك بالترتيب (الأقدم أولًا) وعلامة ↺ على اليوم الذي قضيته فيه.
• سبحة بعدّاد مع إمكانية إضافة أذكارك.
• اختر صوت الأذان والإقامة: تسجيل التطبيق، أو نغمة المنبّه، أو ملف صوت من هاتفك.
• اختصارات على أيقونة التطبيق: القبلة، السبحة، أذكار الصباح، أذكار المساء.
• نسخة احتياطية: ملف تحفظه بنفسك، وكذلك نسخ تلقائي لإعداداتك في حساب جوجل لتعود عند تغيير الهاتف.
• ويدجت للشاشة الرئيسية: الصلاة القادمة، متابعة الصلوات، السبحة، أذكار الصباح والمساء، والتاريخ الهجري والأيام البيض.
• راديو القرآن الكريم مباشر.
• تذكير بالصيام والمناسبات الهجرية، واتجاه القبلة، والطقس.
• «وضع الخلاء»: بضغطة واحدة يوقف كل التنبيهات والأذكار وبضغطة ثانية يعيدها.
• واجهة عربية بالكامل بشكلين: ملكي ومحراب، وخيارات لإخفاء آية اليوم والطقس والاهتزاز.

## Full description (English)
Salat Khair is a calm, ad-free daily companion for prayer and remembrance: accurate prayer times, an adhan and iqama per prayer (sound, vibration or notification only), silencing during prayer, morning/evening and post-prayer adhkar with counters, home-screen widgets, a floating adhkar icon, prayer tracking, live Quran radio, Hijri dates, fasting reminders, qibla and weather. Arabic interface.

## Permission declarations (Play Console > App content)
- Exact alarms (USE_EXACT_ALARM): core function. The app is a prayer-times app: the adhan and iqama must sound at the exact minute of each prayer.
- Foreground service, media playback: adhan audio and live radio, started by the user or the prayer alarm.
- Foreground service, special use: (1) a scheduled reminder notification; (2) a small floating adhkar icon; (3) short dhikr cards over other apps. All are optional and only run when the user turns them on. Prepare a short screen recording showing: Settings > turning the option on > the card/icon appearing > turning it off.
- SYSTEM_ALERT_WINDOW: optional dhikr cards, post-adhan dua, and the "Establish the prayer" reminder over other apps; user enables it in settings.
- ACCESS_NOTIFICATION_POLICY (Do Not Disturb): optional feature that silences the phone during prayer and restores it afterwards.
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: so the system does not delay or kill the adhan alarm; asked only from the "Make sure the adhan works" page.
- RECEIVE_BOOT_COMPLETED: reschedule adhan alarms after restart.
- Location (coarse and fine): find the city for prayer times and qibla; the app works without it by choosing a city.

## Data safety form
- Does the app collect or share user data? Collects: none stored by the developer. Location is processed on the device; approximate coordinates are sent to Open-Meteo and OpenStreetMap Nominatim only to show weather and a place name (third-party processing for app functionality, not stored by us). Answer: "Yes, collects" is NOT required for data processed only on device, but data sent off-device to a service for app functionality should be declared as "Approximate/Precise location, shared, for app functionality, optional".
- Data encrypted in transit: yes (HTTPS). Users can request deletion: the app keeps nothing on a server; uninstalling removes all data.
- No ads, no analytics, no advertising ID.
- Android Auto Backup is on for the settings file only (shared preferences "magiic"): the system may back it up to the user's own Google account and restore it on a new phone. The developer never receives it. In the form this is not "collected by the developer"; mention it in the privacy policy (done).

## Other forms
- Content rating (IARF) questionnaire: no violence, no sexual content, no gambling, no user-generated sharing. Expect "Everyone".
- Target audience: 13 and over (do not pick children).
- Government/financial/health/news apps: none.
- App access: no login required.
- Ads: no.

## Graphics to prepare
- Icon 512×512 PNG and feature graphic 1024×500: ready (supplied by the owner, resized to 512×512 and 1024×500); at least 2 phone screenshots (use 4 to 8): main screen, prayer tracking, adhkar with counter, widgets, side menu, settings.
