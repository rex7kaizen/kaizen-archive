# KAIZEN Archive · Android

KAIZEN Archive, Android telefon üzerinde çalışan, WhatsApp bildirimlerinde gösterilen mesaj metnini yerel olarak arşivleyen bir uygulama başlangıç projesidir. Proje Kotlin, Jetpack Compose, Material 3, Room/SQLite ve DataStore kullanır. APK, GitHub Actions üzerinden derlenir.

## Neler var?

- İlk kurulumda kapalı başlayan, ana ekranda genel mesaj kaydı aç/kapat anahtarı
- Sohbet ayrıntısında sohbet bazlı kayıt aç/kapat anahtarı
- Her sohbet için ayrı mesaj geçmişi görünümü
- Mesaj arama ve sohbet adı üzerinden filtreleme
- Mesajı çöp kutusuna taşıma, geri yükleme ve kalıcı silme
- Sohbeti temizleme veya çöp kutusuna taşıma
- Sohbeti geri yükleme veya kalıcı silme
- Çöp kutusunu onayla boşaltma
- Çöp kutusunda 30 günlük otomatik saklama
- Kullanıcının seçtiği WhatsApp TXT dışa aktarımını içe aktarma
- Açık mor Material 3 arayüz
- Tamamen yerel veritabanı; manifestte INTERNET izni yok

## Dürüst teknik sınırlar

Canlı kayıt için Android `NotificationListenerService` kullanılır ve yalnızca `com.whatsapp` ile `com.whatsapp.w4b` bildirimlerinden Android'in sunduğu metin okunur. Bu, WhatsApp'ın tüm mesaj veritabanına erişim değildir.

Şu durumlarda mesaj metni yakalanamayabilir:

- WhatsApp bildirimleri kapalıysa veya Android bildirim üretmiyorsa
- Bildirim içeriği/önizlemesi gizlenmişse
- Sohbet sessize alındığı veya bildirim kanalı kapatıldığı için bildirim gelmiyorsa
- Android veya cihaz üreticisi arka plan bildirim erişimini kısıtlıyorsa

Uygulama WhatsApp'ın özel veritabanını, şifreli mesajlarını veya WhatsApp'tan silinmiş eski geçmişi okumaz. Uygulamanın kurulumundan önceki mesajlar yalnızca kullanıcının WhatsApp'tan kendi sohbeti için dışa aktardığı TXT dosyasıyla içe alınabilir. İlk sürüm medya eklerini arşivlemez.

## Gizlilik ve depolama

Mesajlar uygulamanın Android özel iç depolamasındaki Room/SQLite veritabanında tutulur. Uygulama internet izni istemez ve mesajları sunucuya göndermez. Android cihazın dosya şifrelemesi mevcut olsa da bu sürümde veritabanına ayrıca SQLCipher veya benzeri uygulama katmanı şifrelemesi eklenmemiştir. Cihaz ekran kilidini kullan.

Bildirim erişimini yalnızca cihazın sahibi Android ayarlarında açıkça etkinleştirir. Kayıt varsayılan olarak kapalıdır; kullanıcı uygulama içinden açmadıkça yeni canlı kayıt başlamaz. Uygulama içindeki kayıt anahtarı kapatılırsa yeni canlı kayıt durur; daha önce arşivlenmiş mesajlar etkilenmez.

## Teknoloji

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21 + Compose Compiler Gradle Plugin
- KSP 2.3.10
- Jetpack Compose BOM 2026.09.00
- Room 2.8.5
- DataStore Preferences 1.3.0
- JDK 17 / Android SDK 36

Sürüm uyumluluğu KSP ve Kotlin'in resmi gereksinimleriyle kontrol edilmiştir. KSP 2.3.10, Kotlin Gradle Plugin 2.2.10–2.3.x, AGP 8.12+ ve Gradle 8.13+ aralığını destekler.

## GitHub Actions

`.github/workflows/android.yml` her `main` push'unda, pull request'te veya manuel tetiklemede unit testleri çalıştırır ve `app-debug.apk` üretir. Başarılı derleme `kaizen-archive-debug-apk` artifact'i olarak saklanır.

Projeyi telefondan GitHub'a yüklemek ve APK'yı indirmek için `TERMUX.md` dosyasındaki adımları izle. Termux'ta Android SDK/Gradle kurmak gerekmez; build GitHub Actions'ta yapılır.

## İlk sürümde sonraki işler

1. Gerçek WhatsApp bildirim çeşitlerini Android 12/MIUI cihazında test etmek.
2. Sohbet başlığı ve grup/özel sohbet ayrımını cihaz testleriyle iyileştirmek.
3. Bildirimlerde tekrar eden mesajların ve özet bildirimlerin ele alınmasını genişletmek.
4. Veritabanı şifrelemesi ve isteğe bağlı uygulama kilidi eklemek.
5. Dışa aktarma, medya meta verisi ve UI otomasyon testleri eklemek.
