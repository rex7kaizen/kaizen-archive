# Termux ile KAIZEN Archive kurulumu ve GitHub'a gönderme

Bu proje Android telefon üzerinden hazırlanıp GitHub Actions ile derlenir. Termux'ta Android SDK kurman veya telefonda Gradle ile APK derlemen gerekmez.

## 1. ZIP'i Termux'a çıkar

Önce ZIP dosyasını Android'in `Download` klasörüne indir. Sonra Termux'ta:

```bash
termux-setup-storage
pkg update -y
pkg install unzip git gh -y
unzip -o /storage/emulated/0/Download/kaizen-archive-android.zip -d "$HOME"
cd "$HOME/kaizen-archive"
```

## 2. GitHub oturumunu kontrol et

```bash
gh auth status
```

Oturum açık değilse:

```bash
gh auth login
```

Git commit kimliği henüz ayarlı değilse şu komutları çalıştır:

```bash
GH_LOGIN="$(gh api user --jq .login)"
GH_ID="$(gh api user --jq .id)"
git config --global user.name "$GH_LOGIN"
git config --global user.email "${GH_ID}+${GH_LOGIN}@users.noreply.github.com"
```

## 3. Yeni ve özel GitHub deposu oluştur

```bash
git init -b main
git add .
git commit -m "Initial KAIZEN Archive Android app"
gh repo create kaizen-archive --private --source=. --remote=origin --push
```

Bu komut yalnızca yeni `kaizen-archive` deposunu oluşturur; başka depolarına dokunmaz.

## 4. APK derlemesini izle

```bash
gh run list --limit 5
```

Listede en üstte görünen Android CI çalışmasının ID numarasını al:

```bash
gh run watch RUN_ID --exit-status
```

`RUN_ID` yerine gerçek sayı yaz. Derleme başarılı olursa APK artifact'ini telefona indir:

```bash
mkdir -p "$HOME/storage/downloads/KAIZEN-Archive"
gh run download RUN_ID -n kaizen-archive-debug-apk -D "$HOME/storage/downloads/KAIZEN-Archive"
```

APK şu klasörde olur:

`/storage/emulated/0/Download/KAIZEN-Archive/app-debug.apk`

## 5. Telefonda ilk kurulum

1. APK'yı kur ve KAIZEN Archive'ı aç.
2. Uygulamadaki bildirim erişimi ayarına git.
3. Android sistem ekranında KAIZEN Archive bildirim erişimini kendin etkinleştir.
4. Uygulamaya dön ve ana ekrandaki kayıt anahtarını kendin aç. İlk kurulumda kayıt varsayılan olarak kapalıdır.
5. WhatsApp'tan izinli bir test sohbetine mesaj geldiğinde arşivde görünüp görünmediğini kontrol et.

## Önemli

- Canlı kayıt yalnızca Android'in WhatsApp bildiriminde verdiği metinle sınırlıdır. Bildirim önizlemesi kapalıysa veya bildirim hiç oluşturulmazsa mesaj metni yakalanamaz.
- WhatsApp'ın özel mesaj veritabanına, şifreli yedeklerine veya silinmiş geçmişine doğrudan erişilmez.
- Geçmiş mesajlar, yalnızca kullanıcının seçtiği WhatsApp TXT dışa aktarımıyla içe alınır.
- Mesajlar uygulamanın özel yerel depolamasında tutulur; sunucuya gönderilmez. İlk sürümde veritabanına ayrıca uygulama katmanı şifrelemesi uygulanmamıştır.
- Bildirim erişimi ve uygulama içindeki kayıt anahtarı iki ayrı kontroldür. İkisi de açık olmalıdır.
