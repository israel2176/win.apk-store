# חנות אפליקציות (Android)

חיפוש חי בגוגל פליי, הורדה דרך APKPure לפי שם חבילה, וטיפול בחסימות נטפרי.

## מבנה

- `data/PlayApi.kt`: חיפוש ודף אפליקציה ישירות מ-play.google.com (פענוח AF_initDataCallback).
  כשגוגל משנה את המבנה, מעדכנים כאן את האינדקסים.
- `data/ApkPure.kt`: אותה קריאה ש-apkeep עושה ל-api.pureapk.com, ומחזירה קישור ל-APK או ל-XAPK.
- `data/NetFree.kt`: זיהוי דף החסימה. `risk-type` פירושו שהקובץ לא נבדק, `deny` פירושו שהוא נחסם.
- `data/Downloads.kt`: ניהול ההורדות. "נסה שוב" תמיד מבקש קישור חדש.
- `install/Installer.kt`: התקנת APK או XAPK (split APKs) בסשן של PackageInstaller.
- `ui/`: המסכים הם דף ראשי, חיפוש, דף אפליקציה והורדות.

השורות בדף הראשי הן חיפושים חיים, ומוגדרות ב-`homeRows` שבקובץ `StoreViewModel.kt`.

## בנייה

פותחים את התיקייה ב-Android Studio ומריצים, או מהמסוף:

    ./gradlew assembleDebug

הקובץ שנוצר: `app/build/outputs/apk/debug/app-debug.apk`

## הערות

- `network_security_config.xml` מאפשר תעודות משתמש, כדי שהחיבור יעבוד עם תעודת נטפרי.
- קבצי OBB (בעיקר במשחקים) שנמצאים בתוך XAPK לא מותקנים בשלב הזה.
