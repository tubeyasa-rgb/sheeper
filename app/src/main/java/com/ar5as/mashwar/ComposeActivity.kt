package com.ar5as.mashwar

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme { App(db,shared) } } }
    }
}

@Composable fun App(db: RideDb, shared: Uri?) {
    val context=LocalContext.current; val dao=db.observations(); val scope=rememberCoroutineScope()
    val items by dao.observe().collectAsState(initial=emptyList())
    val pickups by dao.observePickups().collectAsState(initial=emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15000) } }
    var screen by remember { mutableStateOf(if (shared != null) "capture" else "home") }
    var origin by remember { mutableStateOf("") }; var destination by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }; var recognized by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }; var source by remember { mutableStateOf("USER_ENTERED") }
    var selectedId by remember { mutableLongStateOf(0L) }; var paid by remember { mutableStateOf("") }; var accepted by remember { mutableStateOf("") }; var success by remember { mutableStateOf<Boolean?>(null) }
    var pickupName by remember { mutableStateOf("") }; var pickupNote by remember { mutableStateOf("") }; var pickupStatus by remember { mutableStateOf("UNKNOWN") }
    var forecasts by remember { mutableStateOf<List<Pair<Int,ForecastResult?>>>(emptyList()) }
    LaunchedEffect(items, now / 60000, screen) { if(screen == "forecast") { val reference=items.firstOrNull(); forecasts=withContext(Dispatchers.Default) { listOf(0,5,10,15,30).map { minutes -> minutes to reference?.let { ForecastEngine.forecast(items.filter { row -> row.captured <= now },it,now + minutes * 60_000L) } } } } }
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
    Surface(modifier=Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("أرخص مشوار",style=MaterialTheme.typography.headlineMedium)
        if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.primary)
        when(screen) {
            "home" -> {
                Text("قارن عروضك المحفوظة محليًا؛ لا نضمن استمرار السعر أو تحقيق توفير.")
                Button(onClick={screen="capture"}) { Text("إضافة عرض أو قراءة صورة") }
                Button(onClick={screen="history"}) { Text("المقارنة والسجل") }
                Button(onClick={screen="forecast"}) { Text("التوقعات والتوصيات") }
                Button(onClick={screen="pickups"}) { Text("نقاط الالتقاء والسلامة") }
                Button(onClick={screen="research"}) { Text("البحث والخرائط") }
                Button(onClick={screen="settings"}) { Text("النسخ الاحتياطي والإعدادات") }
            }
            "capture" -> {
                Text("راجع النص، ثم أدخل بيانات العرض وأكّد حفظها. لا تُحفظ الصورة.")
                OutlinedButton(onClick={pick.launch("image/*")}) { Text("اختيار صورة لقراءة النص") }
                if(recognized.isNotBlank()) Text("النص المقروء للمراجعة:\n$recognized",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(origin,{origin=it},label={Text("نقطة الانطلاق")}); OutlinedTextField(destination,{destination=it},label={Text("الوجهة")})
                OutlinedTextField(provider,{provider=it},label={Text("التطبيق")}); OutlinedTextField(category,{category=it},label={Text("فئة الخدمة")}); OutlinedTextField(price,{price=it},label={Text("السعر بالجنيه")})
                Button(onClick={
                    val n=Logic.parsePrice(price); val texts=listOf(origin,destination,provider,category)
                    if(n==null || texts.any { it.isBlank() || it.length>200 }) message="أكمل البيانات وأدخل سعرًا صحيحًا."
                    else { val item=Observation(origin=origin.trim(),destination=destination.trim(),provider=provider.trim(),category=category.trim(),price=n,captured=System.currentTimeMillis(),provenance=source)
                        scope.launch { if(dao.all().any { Logic.duplicate(it,item) }) message="العرض مسجل بالفعل خلال ٩٠ ثانية." else { dao.insert(item); message="تم تأكيد العرض وحفظه محليًا."; price=""; recognized=""; source="USER_ENTERED"; screen="history" } }
                    }
                }) { Text("تأكيد وحفظ العرض") }
            }
            "history" -> {
                Text("قارن عروض المسار والفئة نفسيهما فقط. مدة الحداثة ٣ دقائق من الإدخال.")
                val first=items.firstOrNull(); if(first==null) Text("لا توجد عروض بعد.") else {
                    val candidates=items.filter { Logic.comparable(it,first) && Logic.fresh(it,now) }
                    if(candidates.size>=2) Logic.lowest(candidates,first,now)?.let { Text("أقل عرض حديث: ${it.provider} — ${it.price} جنيه. تحقق قبل الحجز.") } else Text("تحتاج المقارنة عرضين حديثين للرحلة والفئة نفسيهما.")
                }
                items.forEach { item -> HorizontalDivider(); Text("${item.provider} · ${item.category}\n${item.origin} ← ${item.destination}\n${item.price} جنيه · ${if(Logic.fresh(item,now)) "حديث" else "قديم"}\n${DateFormat.getDateTimeInstance().format(Date(item.captured))} · ${if(item.provenance=="USER_CONFIRMED_SCREENSHOT") "صورة أكدها المستخدم" else "إدخال أو استيراد مستخدم"}"); if(item.actualPaid!=null || item.acceptedFare!=null || item.bookingSuccess!=null) Text("نتيجة الرحلة: المدفوع ${item.actualPaid ?: "غير مسجل"}، المقبول ${item.acceptedFare ?: "غير مسجل"}، الحجز ${when(item.bookingSuccess){true->"نجح";false->"لم ينجح";null->"غير مسجل"}}")
                    OutlinedButton(onClick={selectedId=item.id; paid=item.actualPaid?.toString()?:""; accepted=item.acceptedFare?.toString()?:""; success=item.bookingSuccess; screen="feedback"}) { Text("تسجيل نتيجة الرحلة") }
                    OutlinedButton(onClick={scope.launch { dao.delete(item.id) }}) { Text("حذف") } }
                OutlinedButton(onClick={deleteConfirm=true}) { Text("حذف كل السجل") }
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
                OutlinedTextField(destination,{destination=it},label={Text("نص البحث")})
                Button(onClick={ try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q",destination).build())) } catch (_: Exception) { message="لا يوجد متصفح متاح." } }) { Text("فتح بحث عام") }
                Button(onClick={ try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0").buildUpon().appendQueryParameter("q",destination).build())) } catch (_: Exception) { message="لا يوجد تطبيق خرائط متاح." } }) { Text("فتح الخرائط") }
            }
            "settings" -> {
                Text("البيانات على جهازك. إزالة التطبيق تحذفها؛ احتفظ بنسخة مشفرة لاستعادتها. لا تُرفع البيانات تلقائيًا.")
                OutlinedTextField(password,{password=it},label={Text("كلمة مرور النسخة الاحتياطية")},visualTransformation=PasswordVisualTransformation())
                Button(onClick={if(password.length<8) message="استخدم كلمة مرور من ٨ أحرف على الأقل." else export.launch("sheeper-backup.shp")}) { Text("تصدير نسخة مشفرة") }
                Button(onClick={if(password.length<8) message="أدخل كلمة مرور النسخة." else import.launch(arrayOf("application/octet-stream","*/*"))}) { Text("استيراد نسخة مشفرة") }
                Text("الاستيراد يضيف البيانات إلى السجل؛ احتفظ بكلمة المرور لأن استعادتها غير ممكنة دونها.")
            }
        }
        if(screen!="home") TextButton(onClick={screen="home";message=""}) { Text("الرئيسية") }
    } }
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false}, title={Text("حذف كل العروض ونقاط الالتقاء؟")}, text={Text("لا يمكن التراجع عن الحذف إلا باستعادة نسخة احتياطية.")},confirmButton={TextButton(onClick={scope.launch{dao.clearAll(); val legacy=context.getDatabasePath("rides.db"); val removed=!legacy.exists() || context.deleteDatabase("rides.db"); deleteConfirm=false;message=if(removed) "تم حذف السجل ونقاط الالتقاء." else "حُذفت البيانات الحالية، لكن تعذر حذف قاعدة البيانات القديمة."}}){Text("حذف")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("إلغاء")}})
}
