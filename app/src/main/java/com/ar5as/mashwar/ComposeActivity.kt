package com.ar5as.mashwar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db=RideDb.open(applicationContext)
        @Suppress("DEPRECATION") val shared: Uri? = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra(Intent.EXTRA_STREAM) else null
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xff185b48), onPrimary = Color.White, secondary = Color(0xff668b4b), background = Color(0xfff6f7f2), surface = Color.White, onSurface = Color(0xff183a30), outline = Color(0xffbacbc0))) { App(db,shared) }
        } }
    }
}

@Composable fun App(db: RideDb, shared: Uri?) {
    val context=LocalContext.current; val dao=db.observations(); val scope=rememberCoroutineScope()
    val items by dao.observe().collectAsState(initial=emptyList())
    val pickups by dao.observePickups().collectAsState(initial=emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15000) } }
    var screen by rememberSaveable { mutableStateOf(if (shared != null) "capture" else "home") }
    var origin by rememberSaveable { mutableStateOf("") }; var destination by rememberSaveable { mutableStateOf("") }
    var locationInfo by rememberSaveable { mutableStateOf("") }; var locating by remember { mutableStateOf(false) }; var showLocationSettings by remember { mutableStateOf(false) }
    var selectedDestinationCoordinates by rememberSaveable { mutableStateOf<String?>(null) }
    var searchingPlaces by remember { mutableStateOf(false) }; var places by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }; var placesMessage by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }; var recognized by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }; var source by remember { mutableStateOf("USER_ENTERED") }
    var selectedId by remember { mutableLongStateOf(0L) }; var paid by remember { mutableStateOf("") }; var accepted by remember { mutableStateOf("") }; var success by remember { mutableStateOf<Boolean?>(null) }
    var pickupName by remember { mutableStateOf("") }; var pickupNote by remember { mutableStateOf("") }; var pickupStatus by remember { mutableStateOf("UNKNOWN") }
    var forecasts by remember { mutableStateOf<List<Pair<Int,ForecastResult?>>>(emptyList()) }
    LaunchedEffect(items, now / 60000, screen) { if(screen == "forecast") { val reference=items.firstOrNull(); forecasts=withContext(Dispatchers.Default) { listOf(0,5,10,15,30).map { minutes -> minutes to reference?.let { ForecastEngine.forecast(items.filter { row -> row.captured <= now },it,now + minutes * 60_000L) } } } } }
    BackHandler(enabled = screen != "home") { screen = "home"; message = "" }
    fun locate() {
        if (locating) return
        scope.launch {
            locating = true
            showLocationSettings = false
            locationInfo = "بنحدد مكانك..."
            try {
                when (val result = currentLocation(context)) {
                    is LocationResult.Found -> {
                        origin = String.format(java.util.Locale.US, "%.6f,%.6f", result.location.latitude, result.location.longitude)
                        locationInfo = "تم تحديد موقعك. تقدر تعدله قبل فتح الخرائط."
                    }
                    LocationResult.PermissionRequired -> locationInfo = "اسمح بالوصول للموقع عشان نحدد مكانك، أو اكتب نقطة الانطلاق يدويًا."
                    LocationResult.Disabled -> { locationInfo = "شغّل الموقع في إعدادات جهازك، أو اكتب نقطة الانطلاق يدويًا."; showLocationSettings = true }
                    LocationResult.Unavailable -> locationInfo = "ما قدرناش نحدد مكانك دلوقتي. حاول في مكان مفتوح أو اكتب نقطة الانطلاق يدويًا."
                }
            } finally { locating = false }
        }
    }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) locate()
        else locationInfo = "إذن الموقع غير مفعّل. تقدر تكتب نقطة الانطلاق بنفسك."
    }
    fun requestCurrentLocation() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) locate() else requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun findPlaces() {
        val query = destination.trim()
        if (query.isEmpty()) { placesMessage = "اكتب اسم المكان الأول."; return }
        if (query.length > 200) { placesMessage = "اسم المكان طويل جدًا."; return }
        if (searchingPlaces) return
        scope.launch {
            searchingPlaces = true; places = emptyList(); placesMessage = ""
            try {
                when (val result = searchPlaces(context, query)) {
                    is PlaceSearchResult.Found -> if (destination.trim() == query) {
                        places = result.places
                        placesMessage = if (places.isEmpty()) "ملقيناش نتائج. جرّب اسم تاني أو افتح Google Maps." else "اختار المكان من النتائج."
                    }
                    PlaceSearchResult.Unavailable -> placesMessage = "البحث مش متاح على الجهاز دلوقتي. تقدر تفتح Google Maps مباشرة."
                }
            } finally { searchingPlaces = false }
        }
    }
    fun openTripInMaps() {
        val url = MapsLinks.forTrip(origin, selectedDestinationCoordinates ?: destination)
        if (url == null) { message = "اكتب الوجهة الأول عشان نفتحها على الخريطة."; return }
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { message = "مفيش تطبيق خرائط أو متصفح متاح على الجهاز." }
    }
    fun scan(uri: Uri) {
        try {
            message="جارٍ قراءة الصورة على الجهاز..."
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context,uri)).addOnSuccessListener { recognized=it.text.take(5000); message="راجع النص وأدخل السعر بنفسك؛ القراءة قد تخطئ ولا تدعم كل النصوص العربية."; source="USER_CONFIRMED_SCREENSHOT" }.addOnFailureListener { message="تعذرت قراءة الصورة. يمكنك إدخال العرض يدويًا." }.addOnCompleteListener { recognizer.close() }
        } catch (_: Exception) { message="تعذّر فتح الصورة؛ أدخل العرض يدويًا." }
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if(uri != null) scan(uri) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if(uri != null) scope.launch { try { val data=withContext(Dispatchers.IO) { Backup.encode(dao.all(),dao.allPickups(),password) }; withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(data) } }; message="تم حفظ النسخة المشفرة." } catch (_: Exception) { message="تعذر حفظ النسخة." } }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri != null) scope.launch { try { val restored=withContext(Dispatchers.IO) { val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> val output=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); while(true) { val count=input.read(chunk); if(count<0) break; output.write(chunk,0,count); require(output.size()<=5_000_000) }; output.toByteArray() }; Backup.decode(bytes,password) }; dao.importBackup(restored); message="اكتمل الاستيراد: ${restored.observations.size} عرضًا و${restored.pickups.size} نقطة في الملف." } catch (_: Exception) { message="تعذر الاستيراد: كلمة المرور أو الملف غير صالح." } }
    }
    LaunchedEffect(Unit) {
        val prefs=context.getSharedPreferences("migration",0)
        if (!prefs.getBoolean("legacy_imported",false)) {
            val legacy=context.getDatabasePath("rides.db")
            if (legacy.exists()) try {
                val old=withContext(Dispatchers.IO) { SQLiteDatabase.openDatabase(legacy.path,null,SQLiteDatabase.OPEN_READONLY).use { sql -> sql.rawQuery("SELECT origin,destination,provider,category,price,captured FROM quotes",null).use { c -> buildList { while(c.moveToNext()) add(Observation(origin=c.getString(0),destination=c.getString(1),provider=c.getString(2),category=c.getString(3),price=c.getDouble(4),captured=c.getLong(5))) } } } }
                dao.importItems(old); prefs.edit().putBoolean("legacy_imported",true).apply()
            } catch (_: Exception) { message="تعذر استيراد البيانات القديمة؛ بقيت محفوظة على الجهاز." }
            else prefs.edit().putBoolean("legacy_imported",true).apply()
        }
        if (shared != null) scan(shared)
    }
    Surface(modifier=Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp, vertical=18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (screen != "home") {
            TextButton(onClick={screen="home";message=""}, contentPadding=PaddingValues(0.dp)) { Text("‹  الرئيسية", fontWeight=FontWeight.Bold) }
            Text(when(screen) { "capture" -> "إضافة عرض"; "history" -> "عروضي"; "forecast" -> "التوقعات"; "feedback" -> "نتيجة الرحلة"; "pickups" -> "نقاط الالتقاء"; "research" -> "البحث والخرائط"; else -> "الإعدادات" },style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }
        if(message.isNotBlank()) Surface(color=Color(0xffe7f2e6), shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth()) { Text(message, modifier=Modifier.padding(12.dp), color=MaterialTheme.colorScheme.primary) }
        when(screen) {
            "home" -> TripHome(origin=origin, onOriginChange={ origin=it; locationInfo="" }, destination=destination, onDestinationChange={destination=it; selectedDestinationCoordinates=null; places=emptyList(); placesMessage=""}, locating=locating, locationInfo=locationInfo, showLocationSettings=showLocationSettings, searchingPlaces=searchingPlaces, places=places, placesMessage=placesMessage, onLocate={requestCurrentLocation()}, onLocationSettings={ try { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: android.content.ActivityNotFoundException) { locationInfo="افتح إعدادات الجهاز وشغّل الموقع." } }, onFindPlaces={findPlaces()}, onPlaceSelected={ place -> destination=place.label; selectedDestinationCoordinates=String.format(java.util.Locale.US, "%.6f,%.6f", place.latitude, place.longitude); places=emptyList(); placesMessage="تم اختيار المكان." }, onOpenMaps={openTripInMaps()}, onNavigate={screen=it;message=""})
            "capture" -> {
                Text("سجّل السعر اللي ظهر لك في تطبيق المشاوير.", style=MaterialTheme.typography.bodyLarge, color=Color(0xff61756b))
                Surface(color=Color.White,shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("تفاصيل الرحلة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        OutlinedTextField(origin,{origin=it; locationInfo=""},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("نقطة الانطلاق")},shape=RoundedCornerShape(14.dp))
                        OutlinedButton(onClick={requestCurrentLocation()},enabled=!locating,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Text(if(locating) "جارٍ تحديد موقعك..." else "استخدم موقعي الحالي") }
                        if(locationInfo.isNotBlank()) Text(locationInfo,style=MaterialTheme.typography.bodySmall,color=Color(0xff536b61))
                        OutlinedTextField(destination,{destination=it;selectedDestinationCoordinates=null},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("الوجهة")},shape=RoundedCornerShape(14.dp))
                        HorizontalDivider()
                        Text("تفاصيل العرض",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        OutlinedTextField(provider,{provider=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("التطبيق")},shape=RoundedCornerShape(14.dp))
                        OutlinedTextField(category,{category=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("فئة الخدمة")},shape=RoundedCornerShape(14.dp))
                        OutlinedTextField(price,{price=it},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("السعر بالجنيه")},shape=RoundedCornerShape(14.dp))
                        OutlinedButton(onClick={pick.launch("image/*")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Text("اختيار صورة لقراءة النص") }
                        Text("الصورة لا تُحفظ. راجع النص والسعر قبل التأكيد.",style=MaterialTheme.typography.bodySmall,color=Color(0xff6d7c72))
                        if(recognized.isNotBlank()) Text("النص المقروء للمراجعة:\n$recognized",style=MaterialTheme.typography.bodySmall)
                        Button(onClick={
                            val n=Logic.parsePrice(price); val texts=listOf(origin,destination,provider,category)
                            if(n==null || texts.any { it.isBlank() || it.length>200 }) message="أكمل البيانات وأدخل سعرًا صحيحًا."
                            else { val item=Observation(origin=origin.trim(),destination=destination.trim(),provider=provider.trim(),category=category.trim(),price=n,captured=System.currentTimeMillis(),provenance=source)
                                scope.launch { if(dao.all().any { Logic.duplicate(it,item) }) message="العرض مسجل بالفعل خلال ٩٠ ثانية." else { dao.insert(item); message="تم تأكيد العرض وحفظه محليًا."; price=""; recognized=""; source="USER_ENTERED"; screen="history" } }
                            }
                        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)) { Text("تأكيد وحفظ العرض",fontWeight=FontWeight.Bold) }
                    }
                }
            }
            "history" -> {
                Text("العروض لنفس المسار والفئة. يظل العرض حديثًا لمدة ٣ دقائق من إدخاله.", color=Color(0xff61756b))
                val first=items.firstOrNull()
                if(first==null) {
                    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("لسه مفيش عروض",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); Text("أضف أول عرض عشان تبدأ المقارنة.",color=Color(0xff6d7c72)); Button(onClick={screen="capture"}) { Text("إضافة عرض") } } }
                } else {
                    val candidates=items.filter { Logic.comparable(it,first) && Logic.fresh(it,now) }
                    if(candidates.size>=2) Logic.lowest(candidates,first,now)?.let { Text("أقل عرض حديث: ${it.provider} — ${it.price} جنيه. تحقق قبل الحجز.",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold) }
                    else Text("أضف عرضين حديثين لنفس الرحلة والفئة عشان تقارن.",color=Color(0xff6d7c72))
                }
                items.forEach { item ->
                    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(item.provider,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); Text(item.category,style=MaterialTheme.typography.bodySmall,color=Color(0xff718176)) }
                                Text("${item.price} جنيه",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            }
                            Text("${item.origin} ← ${item.destination}")
                            Text("${if(Logic.fresh(item,now)) "حديث" else "قديم"} · ${DateFormat.getDateTimeInstance().format(Date(item.captured))}",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                            Text(if(item.provenance=="USER_CONFIRMED_SCREENSHOT") "صورة أكدها المستخدم" else "إدخال أو استيراد مستخدم",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                            if(item.actualPaid!=null || item.acceptedFare!=null || item.bookingSuccess!=null) Text("نتيجة الرحلة: المدفوع ${item.actualPaid ?: "غير مسجل"}، المقبول ${item.acceptedFare ?: "غير مسجل"}، الحجز ${when(item.bookingSuccess){true->"نجح";false->"لم ينجح";null->"غير مسجل"}}",style=MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={selectedId=item.id; paid=item.actualPaid?.toString()?:""; accepted=item.acceptedFare?.toString()?:""; success=item.bookingSuccess; screen="feedback"}) { Text("تسجيل نتيجة الرحلة") }
                                TextButton(onClick={scope.launch { dao.delete(item.id) }}) { Text("حذف") }
                            }
                        }
                    }
                }
                if(items.isNotEmpty()) TextButton(onClick={deleteConfirm=true}) { Text("حذف كل السجل") }
            }
            "feedback" -> {
                Text("نتيجة الرحلة اختيارية؛ عرض inDrive المقترح لا يعني قبول السائق. أدخل السعر المقبول والمدفوع إن عُرفا.")
                OutlinedTextField(accepted,{accepted=it},label={Text("السعر المقبول جنيه — اختياري")})
                OutlinedTextField(paid,{paid=it},label={Text("المدفوع فعليًا جنيه — اختياري")})
                Text("هل تم الحجز؟")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=success==true,onClick={success=true},label={Text("نعم")})
                    FilterChip(selected=success==false,onClick={success=false},label={Text("لا")})
                    FilterChip(selected=success==null,onClick={success=null},label={Text("غير معروف")})
                }
                Button(onClick={
                    val p=if(paid.isBlank()) null else Logic.parsePrice(paid); val a=if(accepted.isBlank()) null else Logic.parsePrice(accepted)
                    if((paid.isNotBlank()&&p==null)||(accepted.isNotBlank()&&a==null)) message="أدخل مبلغًا صحيحًا أكبر من صفر أو اترك الحقل فارغًا."
                    else scope.launch { dao.setFeedback(selectedId,p,a,success); message="تم حفظ نتيجة الرحلة."; screen="history" }
                }) { Text("حفظ النتيجة") }
            }
            "forecast" -> {
                Text("توقعات أوقات المغادرة من السجل المحلي")
                Text("التوقع يعتمد على ساعة القاهرة في السجل، ولا يعرف تغيّر الطلب اللحظي. الأوقات داخل الساعة قد تتساوى؛ ليست أسعارًا مباشرة ولا ضمانًا للتوفير.")
                val reference=items.firstOrNull()
                if(reference==null) Text(Logic.forecastMessage(items)) else {
                    Text("${reference.provider} · ${reference.category} · ${reference.origin} ← ${reference.destination}")
                    if(forecasts.isEmpty()) Text("جارٍ حساب التوقعات…")
                    forecasts.forEach { (minutes,prediction) ->
                        Text(if(prediction==null) "${if(minutes==0) "الآن" else "بعد $minutes دقيقة"}: بيانات غير كافية أو لم يجتز التحقق الزمني." else "${if(minutes==0) "الآن" else "بعد $minutes دقيقة"}: ${prediction.low}–${prediction.high} جنيه؛ الوسيط ${prediction.median} جنيه. العينات ${prediction.count}، الخطأ ${"%.1f".format(prediction.mae)} جنيه، التغطية ${"%.0f".format(prediction.coverage*100)}٪.")
                    }
                }
                Text("الترتيب الفوري يستخدم العروض الحديثة فقط. لا نوصي بموضع التقاط تلقائي دون تحقق موثوق من سلامة المشاة.")
            }
            "pickups" -> {
                Text("نقاط يحددها المستخدم")
                Text("لا تتوفر بيانات موثوقة للتحقق من قانونية النقطة أو أمانها أو إمكانية الوصول إليها. لا يصنف التطبيق أي نقطة على أنها آمنة ولا يوصي بها تلقائيًا.")
                OutlinedTextField(pickupName,{pickupName=it},label={Text("وصف نقطة الالتقاء")})
                OutlinedTextField(pickupNote,{pickupNote=it},label={Text("ملاحظة اختيارية")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=pickupStatus=="UNKNOWN",onClick={pickupStatus="UNKNOWN"},label={Text("السلامة غير معروفة")})
                    FilterChip(selected=pickupStatus=="UNSAFE",onClick={pickupStatus="UNSAFE"},label={Text("غير آمنة")})
                }
                Button(onClick={ if(pickupName.isBlank()||pickupName.length>200||pickupNote.length>500) message="أدخل وصفًا صالحًا للنقطة." else scope.launch { dao.insertPickup(Pickup(name=pickupName.trim(),status=pickupStatus,note=pickupNote.trim(),recorded=System.currentTimeMillis())); pickupName="";pickupNote="";message="تم حفظ النقطة دون التحقق من سلامتها." } }) { Text("حفظ النقطة") }
                pickups.forEach { point -> HorizontalDivider(); Text("${point.name} · ${if(point.status=="UNSAFE") "غير آمنة حسب إدخالك" else "السلامة غير معروفة"}\n${point.note}"); OutlinedButton(onClick={scope.launch{dao.deletePickup(point.id)}}) { Text("حذف النقطة") } }
            }
            "research" -> {
                Text("البحث العام سياق فقط، وليس سعرًا موثقًا.")
                OutlinedTextField(destination,{destination=it;selectedDestinationCoordinates=null},label={Text("نص البحث")})
                Button(onClick={ try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q",destination).build())) } catch (_: Exception) { message="لا يوجد متصفح متاح." } }) { Text("فتح بحث عام") }
                Button(onClick={ openTripInMaps() }) { Text("افتح في Google Maps") }
            }
            "settings" -> {
                Text("البيانات على جهازك. إزالة التطبيق تحذفها؛ احتفظ بنسخة مشفرة لاستعادتها. لا تُرفع البيانات تلقائيًا.")
                OutlinedTextField(password,{password=it},label={Text("كلمة مرور النسخة الاحتياطية")},visualTransformation=PasswordVisualTransformation())
                Button(onClick={if(password.length<8) message="استخدم كلمة مرور من ٨ أحرف على الأقل." else export.launch("sheeper-backup.shp")}) { Text("تصدير نسخة مشفرة") }
                Button(onClick={if(password.length<8) message="أدخل كلمة مرور النسخة." else import.launch(arrayOf("application/octet-stream","*/*"))}) { Text("استيراد نسخة مشفرة") }
                Text("الاستيراد يضيف البيانات إلى السجل؛ احتفظ بكلمة المرور لأن استعادتها غير ممكنة دونها.")
            }
        }

    } }
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false}, title={Text("حذف كل العروض ونقاط الالتقاء؟")}, text={Text("لا يمكن التراجع عن الحذف إلا باستعادة نسخة احتياطية.")},confirmButton={TextButton(onClick={scope.launch{dao.clearAll(); val legacy=context.getDatabasePath("rides.db"); val removed=!legacy.exists() || context.deleteDatabase("rides.db"); deleteConfirm=false;message=if(removed) "تم حذف السجل ونقاط الالتقاء." else "حُذفت البيانات الحالية، لكن تعذر حذف قاعدة البيانات القديمة."}}){Text("حذف")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("إلغاء")}})
}
