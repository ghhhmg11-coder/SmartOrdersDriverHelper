# Jeeny Ultimate Helper

تطبيق Android للقبول التلقائي لطلبات تطبيق Jeeny مع دعم فلترة المناطق الجغرافية والأسعار.

---

## خطوات رفع المشروع وبناء الـ APK

### 1. تنزيل gradle-wrapper.jar (ضروري جداً)
يجب تنزيل هذا الملف يدوياً ووضعه في `gradle/wrapper/`:

```bash
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
```

### 2. الحصول على Google Maps API Key
1. اذهب إلى https://console.cloud.google.com/
2. أنشئ مشروعاً جديداً أو اختر موجوداً
3. فعّل `Maps SDK for Android`
4. أنشئ API Key من قسم Credentials
5. **للبناء المحلي**: أنشئ ملف `app/secrets.properties` وأضف:
   ```
   MAPS_API_KEY=your_key_here
   ```

### 3. رفع المشروع على GitHub
```bash
git init
git add .
git commit -m "Initial commit: Jeeny Ultimate Helper"
git remote add origin https://github.com/YOUR_USERNAME/JeenyUltimateHelper.git
git push -u origin main
```

### 4. إضافة Secret في GitHub
1. اذهب إلى Settings → Secrets and variables → Actions
2. أضف سر جديد باسم: `MAPS_API_KEY`
3. القيمة: مفتاح Google Maps الخاص بك

### 5. تشغيل GitHub Actions
- سيبدأ البناء تلقائياً عند كل push
- أو اضغط على Actions → Build APK → Run workflow
- بعد اكتمال البناء ستجد الـ APK في:
  - **Actions → الـ workflow → Artifacts** (تحميل مباشر)
  - **Releases** (إصدار منشور)

---

## هيكل الملفات

```
JeenyUltimateHelper/
├── .github/workflows/build.yml     ← GitHub Actions (بناء تلقائي)
├── app/
│   ├── src/main/
│   │   ├── java/com/smartorders/ultimate/
│   │   │   ├── MainActivity.kt           ← الشاشة الرئيسية + ViewPager2
│   │   │   ├── MainPagerAdapter.kt       ← محوّل التبويبات
│   │   │   ├── JeenyUltimateService.kt   ← خدمة القبول التلقائي
│   │   │   ├── FloatingControllerService.kt ← الزر العائم
│   │   │   ├── DashboardFragment.kt      ← لوحة الإحصائيات
│   │   │   ├── MapFragment.kt            ← خريطة المناطق المحظورة
│   │   │   ├── SettingsFragment.kt       ← الإعدادات
│   │   │   └── BootReceiver.kt           ← تشغيل عند إعادة التشغيل
│   │   ├── res/
│   │   │   ├── xml/accessibility_service_config.xml
│   │   │   ├── layout/                   ← واجهات المستخدم
│   │   │   ├── drawable/                 ← أيقونات وخلفيات
│   │   │   └── values/                   ← ألوان، نصوص، ثيمات
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

---

## ميزات التطبيق

| الميزة | الوصف |
|--------|-------|
| القبول التلقائي | يقبل الطلبات تلقائياً من تطبيق Jeeny |
| فلتر السعر | رفض الطلبات دون الحد الأدنى (قابل للضبط) |
| فلتر المناطق | رفض الطلبات من مناطق محظورة على الخريطة |
| الزر العائم | زر تحكم يطفو فوق جميع التطبيقات |
| لوحة الإحصائيات | إحصائيات مرئية للطلبات المقبولة/المرفوضة |
| الخريطة التفاعلية | ضغط مطوّل لإضافة مناطق محظورة |
| التشغيل التلقائي | يبدأ مع إعادة تشغيل الجهاز |

---

## الإعداد الأول على الجهاز
1. ثبّت الـ APK
2. افتح التطبيق
3. اذهب إلى **الإعدادات** → اضغط "فتح إعدادات إمكانية الوصول"
4. فعّل **Jeeny Auto Accept** من القائمة
5. امنح إذن الظهور فوق التطبيقات عند الطلب
6. افتح تطبيق **Jeeny** وابدأ الاستلام
