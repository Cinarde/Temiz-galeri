# Galerini Temizle — Java / Android 11+

Cihazdaki fotoğraf ve videoları çevrimdışı inceleyip Android çöp kutusuna taşıyan uygulama. Backend, hesap, analitik veya ağ çağrısı yoktur. Birleştirilmiş Manifest içinde INTERNET izni bulunmaz. Gradle bağımlılıklarının ilk indirilmesi geliştirme bilgisayarında internet gerektirir.

## Ekranlar ve medya akışı

- **Ayıkla:** fotoğraf ve videolardan oluşan karışık deste, kalan öğe sayacının hemen altında aynı boyutta (12sp) tarih, Ayır / Geri al / Sakla düğmeleri. Tarih kartın üzerinde gösterilmez. Video kartındaki Videoyu oynat düğmesi oynatmayı başlatır; tekrar dokunmak duraklatır.
- **Seçilenler:** iki sütunlu küçük önizlemeler, içerik türü ve tarih, tekil Geri al ve Temizle. Sola kaydırmak dosyayı silmez; onay bekleyen listeye ekler.
- **Menü:** izinler, galeriyi yenileme, işlenenleri tekrar gösterme, kullanım rehberi ve çöp kutusu açıklaması.

`GalleryViewModel` Images ve Video koleksiyonlarını arka plan iş parçacığında sorgular. IS_TRASHED ve IS_PENDING içerikler elenir. İki koleksiyon birleştirilip `Collections.shuffle()` ile rastgele karıştırılır; tarih sıralaması uygulanmaz. Oturum içindeki yenilemeler mevcut kart sırasını korur, yeni içerikleri karışık sırayla ekler. Böylece izin penceresinden dönüş veya geri alma sırasında sıradaki kart değişmez. Yeni oturumda kalan içerikler yeniden karıştırılır.

`GalleryMedia` URI, içerik türü, tarih ve dosya boyutu metadatasını taşır. DATE_TAKEN milisaniye olarak kullanılır; yoksa DATE_ADDED saniyeden milisaniyeye çevrilir. Tarih `dd/MM/yyyy` biçimindedir; eklenme tarihi kullanıldığında etikette açıkça belirtilir. İkisi de yoksa Tarih bilinmiyor gösterilir.

Ayıkla ekranında sayaç ve tarihin sağında, kartın üstündeki “Dosya boyutu” alanı mevcut fotoğraf veya videonun MediaStore SIZE değerini gösterir. KB / MB / GB birimleri 1024 tabanıyla seçilir; ondalık ayracı cihaz diline uyar. Bilgi mevcut arka plan sorgusundan gelir, medya dosyası boyut ölçmek için belleğe yüklenmez. Bilinmeyen boyut sıfır olarak gösterilmez. Kaydırma, geri alma ve yenileme boyut etiketini de günceller; deste boşsa alan gizlenir.

Fotoğraf URI biçimi ve eski SharedPreferences anahtarları korunmuştur. Video URI'leri ayrı `/video/media/` koleksiyonundadır; aynı sayısal ID'ye sahip fotoğrafla çakışmaz.

## Oynatıcı ve bellek

`SwipeDeckView`, `card_media.xml` üzerinden oluşturulan iki kartı yeniden kullanır. Özel ViewPropertyAnimator kaydırması ve Glide önizlemeleri korunur. Glide, fotoğraf veya video küçük resmini en fazla 1080 × 1440 boyutunda yükler; tüm galeriyi bitmap olarak belleğe almaz. Kuyruk önizlemelerini RecyclerView ve Glide yönetir.

Video açıldığında `video_controls.xml` içindeki Media3 kontrolleri görünür: oynat/duraklat, 10 saniye geri/ileri, geçen süre, toplam süre ve sürüklenebilir zaman çubuğu. Kontroller video açıkken görünür kalır. Kontrol alanında başlayan dokunuşlar sarma/oynatma içindir; bu hareketler kartı kaydırmaz veya sayfayı sürüklemez. Kontrol alanı dışında kart kaydırma ve Ayır/Sakla düğmeleri çalışmaya devam eder. Süre ve konum Media3 tarafından güncellenir; ayrıca bir zamanlayıcı tutulmaz.

Media3 ExoPlayer yalnızca **üst karttaki videonun oynat düğmesine basıldığında** oluşturulur. Alt kartta yalnızca önizleme vardır. PlayerView, kart dönüşümlerini desteklemek için TextureView kullanır. Kaydırma başlar başlamaz, düğmeyle karar verildiğinde, kart/sayfa değiştiğinde, Activity durakladığında ve view ayrıldığında oynatıcı durdurulur, PlayerView bağlantısı kesilir ve `release()` çağrılır. Geri alınan videolar otomatik başlamaz. Ses odağı ve kulaklık çıkarma Media3 tarafından yönetilir; oynatma hatasında önizlemeye dönülür.

URI ve metadata listesi bellekte tutulur. Çok büyük galeriler ve yüksek çözünürlüklü/özel codec videoları ayrıca gerçek cihazda profillenmelidir.

## Gradle ve izinler

Uygulama kodu Java, Gradle dosyaları Kotlin DSL'dir. `minSdk=30`, `compileSdk=34`, `targetSdk=34` korunmuştur. Mevcut SDK ile uyumlu bağımlılıklar:

```kotlin
implementation("com.github.bumptech.glide:glide:4.16.0")
implementation("androidx.media3:media3-exoplayer:1.4.1")
implementation("androidx.media3:media3-ui:1.4.1")
implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.5")
implementation("androidx.lifecycle:lifecycle-livedata:2.8.5")
implementation("androidx.recyclerview:recyclerview:1.3.2")
```

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
</manifest>
```

Android 11–12L'de READ_EXTERNAL_STORAGE; Android 13'te IMAGES ve VIDEO; Android 14+'ta bunlarla birlikte VISUAL_USER_SELECTED istenir. Yalnızca bir içerik türü veya sınırlı sayıda öğe için erişim verilirse erişilebilir olanlar gösterilir. `onResume()` izinleri ve MediaStore sonuçlarını yeniler. WRITE_EXTERNAL_STORAGE veya MANAGE_EXTERNAL_STORAGE gerekmez.

Fotoğraf izni olan eski kurulum 1.1 sürümüne güncellendiğinde video izni ilk açılışta bir kez istenir. Fotoğraf ve video izinlerinin daha önce istenip istenmediği ayrı tutulur; eski fotoğraf izni video isteğini engellemez. Sınırlı erişimde Ayıkla ekranındaki “Eksik fotoğraf ve videolara erişim ver” düğmesiyle yeniden seçim yapılabilir. Kalıcı ret durumunda uygulama ayarları açılır. Geçmiş ve bekleyen seçimler güncellemede sıfırlanmaz.

Video sorgusunda uzantı, MIME türü, klasör veya süre kısıtlaması yoktur; Android'in MediaStore Video koleksiyonunda tanıdığı ekran kayıtları da dahildir. Sınırlı erişimde yalnızca izin verilenler gösterilebilir. Listeye erişim ile oynatma desteği ayrıdır: oynatma, Media3'ün kapsayıcı ve cihazın codec desteğine bağlıdır; her özel video biçimi için garanti verilemez.

## Geçmiş, geri alma ve çöp kutusu

`CleaningSession` Android bağımlılığı olmayan karar mantığıdır. Deste ArrayDeque, seçimler LinkedHashSet, karar sırası ArrayDeque ile tutulur. `photo_history` SharedPreferences içindeki `processed_uris`, her iki yönde işlenmiş fotoğraf/video URI'lerini saklar. Her kayıtta yeni HashSet kopyası kullanılır; `apply()` disk yazımını eşzamansız yapar. Daha önce işlenen içerikler yeni desteden filtrelenir.

Bekleyen kuyruk ve karar sırası aynı tercihler dosyasında sıralı JSON olarak korunur. Ortadaki Geri al son sağ veya sol kararı geri çevirir ve URI'yi geçmişten çıkarır. Kuyruktaki tekil Geri al yalnızca o öğeyi destenin başına taşır. Geçici izin kaybı bekleyen seçimleri silmez. Uygulama verilerinin silinmesi geçmişi de sıfırlar.

Deste bittiğinde bekleyen seçimler varsa Seçilenler açılır. Temizle düğmesi en fazla 100 URI için sistem onayı ister; fotoğraf ve videolar aynı işlemde yer alabilir:

```java
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import java.util.Collection;

class TrashExample {
    void requestTrash(ContentResolver resolver, Collection<Uri> uris,
                      ActivityResultLauncher<IntentSenderRequest> launcher) {
        PendingIntent consent = MediaStore.createTrashRequest(resolver, uris, true);
        launcher.launch(new IntentSenderRequest.Builder(consent.getIntentSender()).build());
    }
}
```

Yalnızca RESULT_OK geldiğinde onaylanan grup kuyruktan çıkarılır. İptalde seçimler korunur. Sistem onayı sırasında bekleyen URI kopyası Bundle içinde de saklanır. Uygulama kalıcı silme API'si çağırmaz. Çöp kutusu saklama süresini Android belirler (genellikle 30 gün); belirli bir galeri uygulamasının Son Silinenler ekranındaki görünüm üreticinin MediaStore desteğine bağlıdır.

## Doğrulama

### Android Studio Code Analysis ve Gradle modeli

08/10/2026 incelemesinde IDE'nin 153 hata bildirmesine rağmen temiz Debug/Release derlemeleri, 19 birim testi ve androidTest APK derlemesi başarılıydı. Yerel IDE kaydı son senkronizasyonun ardından önbellek modelinin kullanıldığını gösteriyordu; `external_build_system` içindeki 03/10/2026 tarihli modelde Media3, Glide ve UIAutomator bağımlılıkları bulunmuyordu. Bu model, mevcut Gradle dosyalarıyla aynı değildi. Terminalden build almak IDE'nin bağımlılık modelini senkronize etmez.

Commit/push ekranındaki işleminizi tamamladıktan sonra **File → Sync Project with Gradle Files** çalıştırıp indekslemenin bitmesini bekleyin ve Code Analysis'i yeniden çalıştırın. Push ekranına veya açık IDE önbelleğine dışarıdan müdahale edilmemiştir. Sonucu görmek için denetimleri kapatmak ya da hataları bastırmak gerekmez. Sürüm uyumunu korumak için bağımlılıklar `gradle/libs.versions.toml` kataloğunda tutulur; Media3 ExoPlayer ve UI aynı `media3` sürümünü paylaşır. Video kontrollerinin ikonları uygulamanın kendi kaynaklarıdır; kütüphanenin özel drawable kaynaklarına bağlanmaz.

Düzenlemelerden sonra Debug, imzasız Release ve cihaz testi APK'ları başarıyla üretildi; 19 birim testi geçti. Gradle lint sonucu: 0 hata, 57 uyarı. `PrivateResource` ve `UseTomlInstead` uyarıları kalmadı; kalanlar ağırlıklı olarak yeni sürüm önerileri, kullanılmayan kaynaklar ve metin/düzen önerileridir. Lint raporu `app/build/reports/lint-results-debug.html` yolundadır. IDE'nin 153 hatalık listesi, açık commit/push ekranına dokunulmadığı için yeniden çalıştırılmadı.

Paylaşılan ayrıntılı hata listesi sonrasında video kontrol ID'leri yerel kaynak olarak tanımlandı (`@+id/exo_*`); Media3 aynı birleştirilmiş ID'leri kullanır. Gereksiz kontrol kapsayıcısı kaldırıldı. README XML örneği tek manifest köküne alındı; Java örneği import ve sınıf/metot gövdesiyle tamamlandı ve `javac` ile doğrulandı. Adapter Holder görünürlüğü, null kontrolleri, kuyruk kaldırma işlemleri ve test uyarıları düzeltildi. ViewModelFactory'nin yansıma ile çağırdığı kurucu `@Keep` ile işaretlendi. 19 birim testi ve dört emülatör testi geçti; son kontrol düzeniyle medya oynatma/sarma testi ayrıca tekrar geçti. Debug ve imzasız Release APK'ları üretildi. IDE kontrol aracında `window capture timed out` oluştuğu için IDE Gradle Sync işlemi otomatik başlatılamadı; IDE'de kalan bağımlılık çözümleme hataları senkronizasyon tamamlanınca yeniden kontrol edilmelidir.

Tüm derleme kaynaklarını doğrulamak için:

```powershell
.\gradlew.bat clean :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Emülatör penceresi açılmıyorsa proje terminalinde `powershell -File .\tools\Start-Emulator.ps1` komutunu kullanabilirsiniz. Bu yardımcı Pixel_6_API_36 cihazını görünür pencerede, yazılım grafikleriyle ve eski çökme raporu onayında beklemeden açar. Galeriyi veya uygulama verilerini silmez. Farklı SDK/AVD için `-SdkPath` ve `-AvdName` parametreleri vardır. Zaten çalışan aynı cihaz için ikinci emülatör başlatmaz. Varsayılan SDK, kullanıcının AppData/Local/Android/Sdk klasörüdür.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- `CleaningSessionTest`: kararlar, geri alma, kuyruk ve uzlaştırma.
- `GalleryMediaTest`: tarih önceliği, saniye/milisaniye dönüşümü, eksik tarih, URI ve metadata eşitliği.
- `HistoryPersistenceTest`: fotoğraf/video geçmişinin yeni ViewModel örneklerinde filtrelenmesi, geri alma ve izin kaybında seçimleri koruma.
- `SwipeAndTrashTest`: gerçek sağ/sol dokunma, düğmeler, Android onayını iptal etme/onaylama ve IS_TRASHED kontrolü.
- `MediaPlaybackTest`: gerçek MediaStore fotoğraf/video sorgusu, MediaCodec ile yerel üretilen test videosunun oynatılması, kaydırma/sayfa/arka plan geçişinde oynatıcının kapanması, iki yönde geri alma, karma seçim için Android onayı ve iki dosyada IS_TRASHED kontrolü.
- `VideoPermissionUpgradeTest`: fotoğraf izni verilmiş, video izni verilmemiş ayrı test kurulumunda video iznini isteme ve geçmişin korunmasını doğrulama. Android mevcut medya iznine dayanarak otomatik izin verebilir; pencere gösterirse test onaylar. Bu testten önce test paketinde READ_MEDIA_IMAGES verilmeli, READ_MEDIA_VIDEO geri alınmalıdır.

Cihazdaki uçtan uca testler kullanıcının kurulumunu etkilememek için ayrı paketle çalıştırılır:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PverificationAppId=com.example.galerinitemizle.verification'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.galerinitemizle.MediaPlaybackTest,com.example.galerinitemizle.HistoryPersistenceTest,com.example.galerinitemizle.SwipeAndTrashTest com.example.galerinitemizle.verification.test/androidx.test.runner.AndroidJUnitRunner
```

Kullanıcıya verilecek normal APK için `verificationAppId` parametresi olmadan tekrar `:app:assembleDebug` çalıştırın. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Dosya boyutu güncellemesi doğrulaması: 19 yerel birim testi geçti. API 36 emülatöründe `MediaPlaybackTest` geçti: fotoğraf ve videonun SIZE değerleri gerçek dosya uzunluğuyla karşılaştırıldı; boyutun kart geçişinde ve geri almada güncellendiği, menüde gizlendiği kontrol edildi. Oynatma/sarma, iki yönde geri alma ve karma çöp kutusu onayı da aynı testte doğrulandı. Testler ayrı uygulama paketinde, yalnızca testin ürettiği dosyalarla çalışır. Farklı Android sürümleri ve üretici galerileri ayrıca gerçek cihaz kontrolü gerektirir.

## Kaynaklar

- [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer/hello-world)
- [PlayerView](https://developer.android.com/media/media3/ui/playerview)
- [Android 14 seçili fotoğraf/video erişimi](https://developer.android.com/about/versions/14/changes/partial-photo-video-access)
- [MediaStore.createTrashRequest](https://developer.android.com/reference/android/provider/MediaStore#createTrashRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E,%20boolean))
- [DATE_EXPIRES](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#DATE_EXPIRES)
