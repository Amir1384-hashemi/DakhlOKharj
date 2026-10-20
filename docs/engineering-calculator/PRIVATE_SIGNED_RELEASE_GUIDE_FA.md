# راهنمای آماده‌سازی انتشار پولی «ماشین حساب مهندسی»

آخرین وضعیت: نسخه `1.0.0+10`، شناسه `ir.omranyar.engineeringcalculator`، قیمت هدف `۲۰۰٬۰۰۰ تومان`، ایمیل پشتیبانی `rhashemi284@gmail.com`.

## مدل خرید
برنامه باید از ابتدا در بازار و مایکت **پولی** ثبت شود؛ پرداخت اولیه در خود فروشگاه انجام می‌شود. نرم‌افزار هیچ اشتراک ادواری یا درگاه پرداخت درون‌برنامه‌ای ندارد. قیمت را جداگانه در هر پنل به واحدی که فروشگاه می‌خواهد وارد کنید. تعیین قیمت در فایل‌های پروژه، قیمت واقعی مارکت را تغییر نمی‌دهد.

## محرمانگی و مخزن
مخزن فعلی `Amir1384-hashemi/DakhlOKharj` عمومی و مربوط به پروژه دیگری است. شاخه `engineering-calculator-paid-rc` سورس فشرده برنامه را دارد؛ اشخاص دیگر می‌توانند آن را استخراج کنند. **برای فروش اختصاصی، پروژه را به یک مخزن خصوصی اختصاصی منتقل کنید** و تمام Workflowها و فایل‌های ورودی را در آن قرار دهید. صرف انتخاب گزینه «پولی» در فروشگاه، توزیع APK در خارج از فروشگاه را غیرممکن نمی‌کند.

کلید امضای دائمی را فقط صاحب برنامه ایجاد و نگهداری کند. کلید/رمز را هرگز در چت، Issue، کد، فایل APK یا مستندات عمومی نفرستید. از کلید و رمزها در محلی امن و مستقل نسخه پشتیبان تهیه شود؛ گم‌کردن آن ممکن است مسیر انتشار به‌روزرسانی‌ها را مختل کند.

## روش جایگزین ویندوز: بدون نصب Java (پیشنهادی)

اگر لپ‌تاپ Windows 10/11 دارید و دانلود JDK مشکل است، می‌توان با ابزارهای **داخلی ویندوز** یک گواهی RSA 3072 دارای کلید خصوصی را به صورت `.pfx` صادر کرد. این فایل نوع **PKCS12** است و Gradle/Java در محیط ابری آن را برای امضای اندروید می‌خوانند؛ نیازی به Java روی لپ‌تاپ نیست. سازگاری این روش با Java و امضای JAR در GitHub Actions آزمایش شده است: https://github.com/Amir1384-hashemi/DakhlOKharj/actions/runs/39446680386 .

در بسته ابزار Windows که در گفتگو ارائه شده، بعد از Extract All، فایل `START_CREATE_KEY.cmd` را اجرا کرده و رمز قوی حداقل ۱۶ کاراکتری را دو بار وارد کنید. کلید در `Documents\\EngineeringCalculator\\engineering-calculator-release.pfx` ذخیره می‌شود. کلید/رمز را فقط خودتان نگه دارید و در چت ارسال نکنید. وجود `.pfx` به معنای آماده‌شدن حساب فروشگاهی نیست.

در نسخه جدید `configure_signing.py` نوع فایل JKS یا PFX خودکار تشخیص داده می‌شود و برای PFX، **alias از داخل فایل در محیط ابری پیدا می‌شود**. بنابراین لازم نیست صاحب برنامه برای پیدا کردن alias روی ویندوز Java نصب کند. برای نسخه PFX معمولاً دو GitHub Actions Secret کافی است: `ENGINEERING_CALCULATOR_KEYSTORE_BASE64` و `ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD`. دو Secret alias/key password در صورت نیاز برای فایل‌های قدیمی JKS اختیاری‌اند.

برای تهیه Base64 در Windows PowerShell فقط **در دستگاه خودتان** از دستور زیر استفاده کنید و نتیجه را مستقیماً در **GitHub Secrets مخزن خصوصی** وارد کنید، نه در چت یا فایل‌های عمومی:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'EngineeringCalculator\\engineering-calculator-release.pfx')))
```

## ساخت کلید امضای دائمی در رایانه شخصی
اگر keystore دائمی مخصوص همین برنامه وجود ندارد، روی رایانه‌ای امن با JDK اجرا کنید:

```sh
keytool -genkeypair -v \
  -keystore engineering-calculator-release.jks \
  -storetype PKCS12 \
  -alias engineeringcalculator \
  -keyalg RSA -keysize 3072 -validity 10000
```

رمز keystore و نام alias را محفوظ نگه دارید. برای PKCS12 معمولاً گذرواژه کلید با گذرواژه مخزن یکسان است.

## تنظیم GitHub Secrets
در مخزن خصوصی نهایی: Settings → Secrets and variables → Actions → New repository secret. چهار متغیر را ثبت کنید:

1. `ENGINEERING_CALCULATOR_KEYSTORE_BASE64`: محتوای فایل JKS تبدیل‌شده به Base64، **بدون خط جدید**
2. `ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD`: رمز keystore
3. `ENGINEERING_CALCULATOR_KEY_ALIAS`: نام alias
4. `ENGINEERING_CALCULATOR_KEY_PASSWORD`: رمز کلید

برای فایل‌های JKS قدیمی نیز می‌توان به همین روش از Base64 استفاده کرد. برای تولید Base64 روی Linux از `base64 -w 0 engineering-calculator-release.jks`، روی macOS از `base64 < engineering-calculator-release.jks | tr -d '\\n'` و روی Windows PowerShell از `[Convert]::ToBase64String([IO.File]::ReadAllBytes("engineering-calculator-release.jks"))` استفاده کنید. **خروجی Base64 هم محرمانه است.**

## اجرای خروجی تجاری
Workflow موجود: `.github/workflows/engineering-calculator-signed-release.yml`.

نکته: `workflow_dispatch` روی شاخه غیرپیش‌فرض GitHub بدون Workflow متناظر در شاخه پیش‌فرض ممکن است قابل اجرا نباشد. برای همین، یک راه مستقل تعریف شده است: بعد از تنظیم Secrets، روی شاخه `engineering-calculator-paid-rc` فایل `ci/engineering-calculator-store/release-request.txt` را **ایجاد یا ویرایش و Commit** کنید. فقط این فایل باعث شروع خودکار Workflow انتشار امضاشده می‌شود. **تا قبل از واردکردن کلیدهای واقعی، این کار را انجام ندهید.**

خروجی موفق:
- `app-release.apk`: فایل نصب امضاشده
- `app-release.aab`: Android App Bundle امضاشده برای فروشگاه‌هایی که آن را قبول می‌کنند
- `SHA256SUMS.txt`: اثرانگشت فایل‌ها

هر فایل تنها به‌صورت artifact موقت GitHub Actions برای مالک/افراد مجاز قرار می‌گیرد و نباید به‌صورت عمومی منتشر شود. بسته را طبق شرایط هر فروشگاه در پنل همان فروشگاه بارگذاری کنید.

## آزمایش عملی ضروری
- نصب نسخه امضاشده روی گوشی Android واقعی
- بررسی نام، آیکن، شماره نسخه و بسته `ir.omranyar.engineeringcalculator`
- سنجش ماشین‌حساب، انتگرال و مشتق، نمودار، مقطع و خرپا با ورودی نمونه
- آزمایش آفلاین، نمایش صحیح اعداد فارسی، سایزبندی صفحه و خطاهای ورودی
- ارتقای نسخه امضاشده با همان keystore (برای به‌روزرسانی آتی، versionCode بالاتر)
- تعیین سیاست حریم خصوصی نهایی، ایمیل پشتیبانی و تصاویر واقعی صفحه
- رعایت مقررات هر فروشگاه و تأیید اطلاعات مالی و هویتی

**اطلاعات باقی‌مانده در اختیار صاحب برنامه/فروشگاه:** هویت و شماره حساب تسویه، اعتبارسنجی حساب توسعه‌دهنده، کلید امضای اصلی، تصاویر واقعی و تأیید نهایی منتشرشدن برنامه.

**لینک خرید رسمی** فقط پس از پذیرش انتشار برنامه توسط بازار/مایکت وجود خواهد داشت. لینک یک APK خام، روش فروش یک‌باره را تضمین نمی‌کند.
