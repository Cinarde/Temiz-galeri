# Galerini Temizle — Java / Android 11+

Cihazdaki fotoğrafları çevrimdışı inceleyip Android çöp kutusuna taşımak için temel uygulama. Backend, hesap, analitik veya ağ çağrısı yoktur. Birleştirilmiş Manifest içerisinde INTERNET izni bulunmaz. Gradle bağımlılıklarının ilk indirilmesi geliştirme bilgisayarında internet gerektirir; uygulamanın çalışması gerektirmez.

## Mimari

- `MainActivity`: izinler, ekran durumu, düğmeler, Activity Result API ve sistem çöp kutusu onayı.
- `GalleryViewModel`: ekran döndürülürken oturumu korur. MediaStore sorgusunu tek bir arka plan iş parçacığında çalıştırır; yalnızca URI listesini ana iş parçacığına aktarır. Eski sorgu sonuçlarını sürüm sayacıyla eler.
- `CleaningSession`: Android bağımlılığı olmayan karar mantığı. Deste `ArrayDeque`, silinecekler ekleme sırasını koruyan `LinkedHashSet`, son sola kaydırma geçmişi `ArrayDeque` olarak tutulur. Aynı fotoğraf kuyruğa iki kez eklenmez.
- `SwipeDeckView`: iki kartı yeniden kullanır. Öndeki kart dokunmayla veya düğmelerle 220 ms içerisinde çevrilip kaydırılır. Eşik aşılmazsa yerine döner. Animasyon boyunca tekrar işlem engellenir. CardStackView yerine Android `ViewPropertyAnimator` kullanılır; JitPack gerekmez.
- `activity_main.xml`: üç ayrı sayfayı barındıran alan ve alt gezinme çubuğu. `page_sort.xml` fotoğraf destesini, `page_queue.xml` önizleme listesini ve temizleme işlemini, `page_menu.xml` izinleri ve rehberi içerir. `QueueAdapter` yalnızca görünür küçük önizlemeleri Glide ile yükler.

Akış: MediaStore → URI destesi → sağ: sakla / sol: kuyruğa ekle → isteğe bağlı geri al → Temizle → Android onayı → onaylanan URI'leri kuyruktan çıkar.

## Gradle

Proje Kotlin DSL kullanır; uygulama kodunun tamamı Java'dır. `minSdk = 30`, `compileSdk = 34`, `targetSdk = 34` mevcut proje ayarlarıdır. Kurulu SDK ile derlenmesi için AndroidX/Material sürümleri `gradle/libs.versions.toml` içinde uyumlu sürümlere sabitlenmiştir. Bu bir temel proje yapılandırmasıdır.

`app/build.gradle.kts` ekleri:

```kotlin
implementation("com.github.bumptech.glide:glide:4.16.0")
implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.5")
implementation("androidx.lifecycle:lifecycle-livedata:2.8.5")
```

Standart `Glide.with(...)` kullanıldığı için annotation processor gerekmez. `google()` ve `mavenCentral()` zaten `settings.gradle.kts` içinde bulunur.

Fotoğraflar `content://media/...` URI'leri üzerinden Glide ile yüklenir. `asBitmap()` animasyonlu dosyalarda tek kare gösterir; `override(...)` önizlemeyi en fazla 1080 × 1440 piksel ile sınırlar, `fitCenter()` kırpmadan gösterir. Yalnızca iki ImageView kullanılır, orijinal boyutta decode yapılmaz, fotoğraf kopyası disk önbelleğine yazılmaz. Glide bellek önbelleğini ve istek yaşam döngüsünü yönetir. Her cihaz/görsel için mutlak OOM veya takılmama garantisi verilemez; büyük galeri ve düşük bellekli gerçek cihazlarda profil çıkarılmalıdır. Bu temel sürüm tüm URI metadatasını bellekte tutar; bitmapleri topluca tutmaz.

## Manifest ve çalışma zamanı izinleri

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
```

- Android 11–12L: `READ_EXTERNAL_STORAGE`.
- Android 13: `READ_MEDIA_IMAGES`.
- Android 14+: `READ_MEDIA_IMAGES` ve `READ_MEDIA_VISUAL_USER_SELECTED` birlikte istenir; tam, sınırlı ve reddedilmiş erişim ayrı değerlendirilir.
- Sınırlı erişimde sadece kullanıcının izin verdiği fotoğraflar listelenir. Erişim düğmesi yeniden seçim açabilir. Kalıcı ret veya tam erişimin değiştirilmesi için uygulama ayarları açılır.
- `onResume()` izinleri yeniden kontrol eder ve fotoğrafları tekrar sorgular. Erişilemeyen fotoğraflar desteden çıkarılır; silinmeyi bekleyen seçimler kuyrukta korunur.
- `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA`, `requestLegacyExternalStorage` gerekmez.

## Çöp kutusu kodu

Çalışan uygulama `MainActivity.requestTrash()` içinde en fazla 100 URI içeren sabit bir işlem kopyası oluşturur. Bu sınır uygulamanın Binder yükünü küçük tutma tercihidir. Daha büyük kuyrukta kullanıcıya bilgi verilir; her sonraki grup için Temizle'ye yeniden basılır ve ayrı sistem onayı gerekir.

```java
ArrayList<Uri> uris = new ArrayList<>();
for (String value : model.session.trashBatch(100)) {
    uris.add(Uri.parse(value));
}
if (uris.isEmpty()) return;

PendingIntent consent = MediaStore.createTrashRequest(
        getContentResolver(), uris, true);
trashLauncher.launch(new IntentSenderRequest.Builder(
        consent.getIntentSender()).build());
```

`true`, `IS_TRASHED` durumunu etkinleştirmek anlamına gelir. Sadece isteği oluşturmak fotoğrafı taşımaz; PendingIntent başlatılarak sistem onayı gösterilir.

```java
private final ActivityResultLauncher<IntentSenderRequest> trashLauncher =
    registerForActivityResult(
        new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                model.confirmTrashed(model.pendingTrash);
            }
            // Ret/iptalde kuyruktaki fotoğraflar korunur.
            model.pendingTrash.clear();
            model.requestingTrash = false;
            refresh();
        });
```

Tam hata yönetimi, bildirimler ve işlem kopyasının oluşturulması `MainActivity.java` dosyasındadır. Android `RESULT_OK` döndürmeden önce işlemi tamamlar. Bundan sonra `ContentResolver.delete()` çağrılmaz; uygulamada kalıcı silme yolu yoktur.

## Geri al ve oturum sınırları

A sola, B sağa kaydırıldıysa ortadaki Geri al düğmesi önce B'yi, tekrar basılırsa A'yı destenin en üstüne getirir. Sağ ve sol kararların sırası SharedPreferences içinde saklanır. Seçilenler sayfasındaki tekil Geri al yalnızca ilgili fotoğrafı geri getirir. Android tarafından çöp kutusuna taşınmış fotoğraflar bu düğmeyle geri gelmez. Eski sürümde sağ kaydırmaların sırası kaydedilmediğinden bunlar için geriye dönük sıra üretilemez; yeni kaydırmalar iki yönde de geri alınabilir.

ViewModel ekran döndürmede oturumu korur. Sağa ve sola kaydırılan fotoğrafların URI’leri `photo_history` SharedPreferences dosyasında `processed_uris` anahtarıyla StringSet olarak saklanır. Uygulama başlarken bu kümenin bir HashSet kopyası alınır. MediaStore sorgusu her URI için bu kopyayı kontrol eder ve daha önce işlenmiş fotoğrafları yeni desteye eklemez.

Silinecekler kuyruğu da aynı tercihler dosyasında sıralı JSON olarak kaydedilir; böylece sola kaydırılan fotoğraflar uygulama yeniden açıldığında Seçilenler sayfasından onaylanabilir veya geri alınabilir. Kuyruğun erişilebilirliği ile destenin geçmiş filtresi ayrı değerlendirilir. Geçici olarak erişilemeyen seçimlerin kalıcı kaydı korunur. Geri alma hem tekil geri alma düğmesinde hem son silme seçimini geri almada URI’yi geçmişten çıkarır. Android onayıyla çöp kutusuna taşınanların işlenmiş geçmişi korunur.

`putStringSet` her kayıtta yeni bir HashSet kopyası alır; SharedPreferences’ın döndürdüğü kümeye doğrudan müdahale edilmez. `apply()` disk yazımını eşzamansız yapar. URI karşılaştırmaları ortalama O(1) maliyetlidir. Geçmiş ve onay bekleyen kuyruk aynı Editor işleminde kaydedilir. Sistem onayı açıkken işlem kopyası ayrıca Bundle içinde korunur. Uygulama verileri silinirse geçmiş de sıfırlanır. Bu sürümden önceki kaydırmaların geçmiş kaydı bulunmadığı için geriye dönük filtrelenmesi mümkün değildir.

## Saklama süresi ve Son Silinenler

MediaStore çöp kutusu süresi `DATE_EXPIRES` üzerinden sistem tarafından belirlenir; genellikle 30 gündür. Uygulama tam 30 gün garantisi vermez. Süre dolduğunda sistem kalıcı temizlik yapabilir. Fotoğrafların belirli bir galeri uygulamasındaki “Son Silinenler” ekranında nasıl göründüğü o uygulamanın MediaStore çöp kutusu desteğine bağlıdır; tüm üreticiler veya bulut galerileri için aynı arayüz garantisi yoktur.

Resmî kaynaklar:
- [MediaStore.createTrashRequest](https://developer.android.com/reference/android/provider/MediaStore#createTrashRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E,%20boolean))
- [DATE_EXPIRES ve saklama süresi](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#DATE_EXPIRES)
- [Android 14 seçili fotoğraf erişimi](https://developer.android.com/about/versions/14/changes/partial-photo-video-access)
- [Glide 4.16.0](https://github.com/bumptech/glide/releases/tag/v4.16.0)

## Doğrulama

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`CleaningSessionTest` için 7 senaryo: sağa kaydırma, aradaki sağ kaydırmalardan sonra geri alma, tekrar seçmede tekilleştirme, iptalde kuyruğu koruma, yalnızca onaylanan grubu çıkarma, erişim değişiminde uzlaştırma ve boş deste.

Gerçek cihaz kontrolü: Android 11/13/14+ izin verme/reddetme/sınırlı seçim, büyük fotoğraflar, hızlı kaydırma, son kartta geri al, ekran döndürme, sistem onayını iptal etme/onaylama, 100'den fazla seçimin grupları ve cihaz galerisinden çöp kutusunu geri yükleme. Test için kopya fotoğraflar kullanın.

Son yerel doğrulama: Debug APK üretildi, 7 CleaningSession testi (ve başlangıç şablonunun 1 testi) geçti, lint 0 hata / 34 uyarı bildirdi. API 36 emülatöründe uygulama açıldı; izin öncesi ekran görüntüsü incelendi ve AndroidRuntime hata kaydı görülmedi. Fotoğrafları gerçekten çöp kutusuna taşıma ve üretici galerisinden geri yükleme akışı henüz cihazda doğrulanmadı.

## Yeni arayüz

Alt gezinme ile üç ekran ayrıldı:

- **Ayıkla:** fotoğrafa ayrılan geniş alan, kalan sayısı, Ayır / Geri al / Sakla düğmeleri. Kart yüksekliği ekran alanına göre hesaplanır; dar ekranlarda sayfa kaydırılabilir.
- **Seçilenler:** iki sütunlu önizleme listesi (geniş ekranlarda üç), her fotoğraf için Geri al ve sabit Temizle alanı. Geri alınan fotoğraf destenin başına gelir. Alt menü rozeti bekleyen sayıyı gösterir.
- **Menü:** erişim durumu, izinleri değiştirme, galeriyi yenileme, kullanım rehberi ve çöp kutusu açıklaması.

Açık/koyu tema için yeşil, krem ve mercan renk paletleri eklendi. Android geri tuşu alt sayfalardan Ayıkla ekranına döner. Seçili sayfa ekran yeniden oluşturulduğunda korunur.

UI değişikliği doğrulaması: Debug APK ve lint başarılı (0 hata). 8 oturum testi geçti. API 36 emülatöründe gezinme, Activity yeniden oluşturma, kuyruk önizlemesindeki Geri al ve boş liste akışı doğrulandı. Ana ekran düğmelerinin alt menünün üzerinde görünür kaldığı ayrıca kontrol edildi. Emülatör ekran görüntülerindeki manzara gerçek kullanıcı fotoğrafı değil, testin ürettiği örnek görseldir.


## Geri al ve bekleyen seçimler düzeltmesi

- Merkezdeki Geri al artık son sağ veya sol kaydırmayı geri alır; erişilebilir kaydırma sırası uygulama yeniden açıldığında korunur.
- Galeri erişimi değiştiğinde bekleyen seçimler listeden silinmez. Önizleme okunamasa bile seçim görünür kalır.
- Deste bittiğinde bekleyen seçimler varsa Seçilenler sayfası otomatik açılır. Sola kaydırma yalnızca seçimdir; gerçek taşıma Seçilenler → Temizle → Android onayı ile yapılır.
- Menü → İşlenen fotoğrafları tekrar göster, eski filtrelenmiş orijinalleri yeniden incelemeyi sağlar. Bekleyen silme seçimlerini korur, dosya silmez.

Doğrulama: 12 oturum testi, kalıcılık/geri alma cihaz testi ve gerçek dokunmalı uçtan uca cihaz testi geçti. Son test hem sağ/sol hareketleri ve merkez düğmesini hem de Android çöp kutusu penceresinin iptal/onay yollarını çalıştırır. Onay sonrası test fotoğrafında `IS_TRASHED = 1`, saklanan diğer iki fotoğrafta `IS_TRASHED = 0` sorguyla doğrulandı. Yalnızca testin oluşturduğu fotoğraflar kullanıldı.

Gerçek dokunmalı test, kullanıcı kurulumunu etkilememek için ayrı paket gerektirir:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PverificationAppId=com.example.galerinitemizle.verification'
```

Bu APK'lar ayrı paket olarak kurulduktan sonra `SwipeAndTrashTest` çalıştırılabilir. Kullanıcıya verilecek normal APK için `verificationAppId` parametresini kullanmadan tekrar `:app:assembleDebug` çalıştırın.
