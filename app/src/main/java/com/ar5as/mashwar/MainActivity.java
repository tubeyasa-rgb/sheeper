package com.ar5as.mashwar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private SQLiteDatabase db;
    private LinearLayout root;
    private EditText origin, destination, provider, category, price;
    private static final long FRESH_MS = 3 * 60 * 1000L;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new SQLiteOpenHelper(this, "rides.db", null, 1) {
            public void onCreate(SQLiteDatabase d) { d.execSQL("CREATE TABLE quotes (id INTEGER PRIMARY KEY, origin TEXT NOT NULL, destination TEXT NOT NULL, provider TEXT NOT NULL, category TEXT NOT NULL, price REAL NOT NULL, captured INTEGER NOT NULL)"); }
            public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) { }
        }.getWritableDatabase();
        if (Intent.ACTION_SEND.equals(getIntent().getAction()) && getIntent().getType() != null && getIntent().getType().startsWith("image/")) showEntry(); else showHome();
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xff182431); t.setPadding(0, 10, 0, 10); return t;
    }
    private void page(String title) {
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 32, 32, 32); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setBackgroundColor(0xfff6f8f7); scroll.addView(root); setContentView(scroll);
        TextView heading = text(title, 28); heading.setTextColor(0xff145c4b); root.addView(heading);
    }
    private EditText field(String hint, boolean numeric) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); root.addView(e); return e;
    }
    private void button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); root.addView(b);
    }
    private void showHome() {
        page("أرخص مشوار");
        root.addView(text("قارن عروض المشاوير التي أدخلتها بنفسك. بياناتك محفوظة على جهازك فقط.", 17));
        root.addView(text("ابدأ بإدخال عرض حقيقي من تطبيق المشاوير. الأسعار تتغير، وقد لا تتطابق العروض مع الرحلة عند الحجز.", 15));
        button("إضافة عرض", this::showEntry);
        button("مقارنة العروض والسجل", this::showHistory);
        button("بحث عام عن المشوار", this::showResearch);
        root.addView(text("التوقعات ونقاط الالتقاء البديلة غير متاحة حاليًا لعدم توفر بيانات كافية وبيانات مشاة موثوقة.", 15));
    }
    private void showResearch() {
        page("بحث عام");
        root.addView(text("يفتح البحث متصفحك أو تطبيق الخرائط. نتائج البحث معلومات عامة وليست عروض أسعار مؤكدة.",16));
        EditText query=field("اكتب المكان أو موضوع البحث",false);
        button("بحث في الويب", () -> openSearch(query.getText().toString(), false));
        button("بحث في الخرائط", () -> openSearch(query.getText().toString(), true));
        button("رجوع", this::showHome);
    }
    private void openSearch(String query, boolean maps) {
        if (query.trim().isEmpty()) { Toast.makeText(this,"أدخل نص البحث",Toast.LENGTH_SHORT).show(); return; }
        Uri uri=Uri.parse(maps ? "geo:0,0" : "https://www.google.com/search").buildUpon().appendQueryParameter("q",query.trim()).build();
        try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch (android.content.ActivityNotFoundException e) { Toast.makeText(this,"لا يوجد تطبيق متاح لفتح البحث",Toast.LENGTH_LONG).show(); }
    }
    private void showEntry() {
        page("إضافة عرض يدوي");
        root.addView(text("اكتب بيانات العرض كما ظهرت لك، وقارن العروض للرحلة وفئة الخدمة نفسها.", 16));
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            root.addView(text("وصلت صورة للمراجعة. قراءة النص تلقائيًا غير متاحة بعد؛ أدخل العرض بنفسك. لا تُحفظ الصورة.", 15));
            Uri image = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (image != null) { ImageView preview = new ImageView(this); preview.setAdjustViewBounds(true); preview.setMaxHeight(500); preview.setImageURI(image); root.addView(preview); }
        }
        origin=field("نقطة الانطلاق",false); destination=field("الوجهة",false); provider=field("اسم التطبيق",false); category=field("فئة الخدمة",false); price=field("السعر بالجنيه المصري",true);
        button("حفظ العرض", () -> save()); button("رجوع", this::showHome);
    }
    private void save() {
        String a=origin.getText().toString().trim(), b=destination.getText().toString().trim(), p=provider.getText().toString().trim(), c=category.getText().toString().trim();
        double amount;
        try { amount=Prices.parsePrice(price.getText().toString()); } catch (NumberFormatException ex) { amount=Double.NaN; }
        if (a.isEmpty() || b.isEmpty() || p.isEmpty() || c.isEmpty() || !Double.isFinite(amount) || amount <= 0 || amount > 1000000) { Toast.makeText(this,"أكمل البيانات وأدخل سعرًا صالحًا أكبر من صفر",Toast.LENGTH_LONG).show(); return; }
        long now = System.currentTimeMillis();
        try (Cursor duplicates = db.rawQuery("SELECT id FROM quotes WHERE lower(trim(origin))=lower(trim(?)) AND lower(trim(destination))=lower(trim(?)) AND lower(trim(provider))=lower(trim(?)) AND lower(trim(category))=lower(trim(?)) AND price=? AND captured>=? AND captured<=? LIMIT 1",new String[]{a,b,p,c,Double.toString(amount),Long.toString(now-90000),Long.toString(now)})) {
            if (duplicates.moveToFirst()) { Toast.makeText(this,"هذا العرض مسجل بالفعل خلال آخر ٩٠ ثانية",Toast.LENGTH_LONG).show(); return; }
        }
        ContentValues values = new ContentValues(); values.put("origin",a); values.put("destination",b); values.put("provider",p); values.put("category",c); values.put("price",amount); values.put("captured",System.currentTimeMillis()); db.insertOrThrow("quotes",null,values); showHistory();
    }
    private void showComparison() {
        long now = System.currentTimeMillis();
        try (Cursor last = db.rawQuery("SELECT origin,destination,category FROM quotes ORDER BY captured DESC LIMIT 1", null)) {
            if (!last.moveToFirst()) return;
            String a=last.getString(0), b=last.getString(1), c=last.getString(2);
            try (Cursor matches=db.rawQuery("SELECT provider,price,captured FROM quotes WHERE lower(trim(origin))=lower(trim(?)) AND lower(trim(destination))=lower(trim(?)) AND lower(trim(category))=lower(trim(?)) AND captured>=? AND captured<=? ORDER BY price ASC", new String[]{a,b,c,Long.toString(now-FRESH_MS),Long.toString(now)})) {
                if (matches.getCount()<2) { root.addView(text("المقارنة تحتاج عرضين حديثين على الأقل لنفس الرحلة والفئة.",15)); return; }
                matches.moveToFirst();
                root.addView(text(String.format(Locale.getDefault(),"أقل عرض مُدخل حديثًا للرحلة الأخيرة: %s — %.2f جنيه. تحقق من التفاصيل في التطبيق قبل الحجز.",matches.getString(0),matches.getDouble(1)),18));
            }
        }
    }
    private void showHistory() {
        page("العروض المحفوظة");
        root.addView(text("العرض حديث لمدة ٣ دقائق من وقت إدخاله. قارن يدويًا الرحلات والفئات المتطابقة فقط؛ لا توجد أسعار مباشرة أو توقعات مضمونة.", 15));
        showComparison();
        try (Cursor rows=db.rawQuery("SELECT id,origin,destination,provider,category,price,captured FROM quotes ORDER BY captured DESC",null)) {
            if (rows.getCount()==0) root.addView(text("لا توجد عروض محفوظة بعد.",18));
            while (rows.moveToNext()) {
                long id=rows.getLong(0), timestamp=rows.getLong(6); boolean fresh=System.currentTimeMillis() >= timestamp && System.currentTimeMillis()-timestamp <= FRESH_MS;
                String label=String.format(Locale.getDefault(),"%s — %s\n%s ← %s\n%.2f جنيه · %s\nأُدخل: %s · إدخال يدوي", rows.getString(3),rows.getString(4),rows.getString(1),rows.getString(2),rows.getDouble(5),fresh?"حديث":"قديم",DateFormat.getDateTimeInstance().format(new Date(timestamp)));
                TextView card=text(label,17); card.setPadding(20,20,20,20); card.setBackgroundColor(0xffffffff); root.addView(card);
                button("حذف هذا العرض", () -> new AlertDialog.Builder(this).setMessage("حذف العرض؟").setNegativeButton("إلغاء",null).setPositiveButton("حذف",(d,w)->{db.delete("quotes","id=?",new String[]{Long.toString(id)});showHistory();}).show());
            }
        }
        button("حذف جميع العروض", () -> new AlertDialog.Builder(this).setMessage("حذف جميع العروض نهائيًا؟").setNegativeButton("إلغاء",null).setPositiveButton("حذف",(d,w)->{db.delete("quotes",null,null);showHistory();}).show());
        button("إضافة عرض",this::showEntry); button("الرئيسية",this::showHome);
    }
}
