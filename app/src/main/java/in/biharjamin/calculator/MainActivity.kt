package in.biharjamin.calculator

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.floor

data class LandProfile(val name: String, val katha: Double, val dhur: Double, val decimal: Double) {
    fun json() = JSONObject().put("name", name).put("katha", katha).put("dhur", dhur).put("decimal", decimal)
    companion object {
        fun from(j: JSONObject) = LandProfile(j.optString("name"), j.optDouble("katha",1620.0), j.optDouble("dhur",81.0), j.optDouble("decimal",435.6))
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF176337))) { LandApp() } }
    }
}
@Composable
fun NumField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value=value, onValueChange=onValue, label={Text(label)}, modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal), singleLine=true)
}
fun n(s:String)=s.toDoubleOrNull()?.takeIf{it.isFinite()}?.coerceAtLeast(0.0) ?: 0.0
fun f(v:Double)=String.format(Locale.US,"%.3f",v)
fun safeName(s:String)=s.trim().ifBlank{"Land_Calculation"}.replace(Regex("[^A-Za-z0-9_-]"),"_")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandApp() {
    val ctx= LocalContext.current
    val prefs= remember { ctx.getSharedPreferences("land_calc",0) }
    var english by remember { mutableStateOf(prefs.getBoolean("english",false)) }
    fun t(hi:String,en:String)=if(english) en else hi

    val savedProfiles= remember {
        val arr=runCatching { JSONArray(prefs.getString("profiles","[]")) }.getOrDefault(JSONArray())
        buildList { for(i in 0 until arr.length()) add(LandProfile.from(arr.getJSONObject(i))) }
    }
    var profiles by remember { mutableStateOf(savedProfiles.ifEmpty { listOf(LandProfile("Gopalganj (Default)",1620.0,81.0,435.6)) }) }
    var selected by remember { mutableStateOf(prefs.getString("selected","Gopalganj (Default)") ?: "Gopalganj (Default)") }
    var profileName by remember { mutableStateOf(selected) }
    var kathaSq by remember { mutableStateOf(profiles.firstOrNull{it.name==selected}?.katha?.toString() ?: "1620") }
    var dhurSq by remember { mutableStateOf(profiles.firstOrNull{it.name==selected}?.dhur?.toString() ?: "81") }
    var decimalSq by remember { mutableStateOf(profiles.firstOrNull{it.name==selected}?.decimal?.toString() ?: "435.6") }
    var katha by remember { mutableStateOf("1") }; var dhur by remember { mutableStateOf("0") }
    var decimal by remember { mutableStateOf("") }; var length by remember { mutableStateOf("") }; var width by remember { mutableStateOf("") }
    var plotName by remember { mutableStateOf("") }; var owner by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var history by remember {
        mutableStateOf(runCatching { JSONArray(prefs.getString("history","[]")) }.getOrDefault(JSONArray()).let { a -> buildList { for(i in 0 until a.length()) add(a.getString(i)) } })
    }
    val ks=n(kathaSq); val ds=n(dhurSq); val decs=n(decimalSq)
    val valid=ks>0 && ds>0 && decs>0

    fun persistProfiles() {
        val a=JSONArray(); profiles.forEach{a.put(it.json())}; prefs.edit().putString("profiles",a.toString()).putString("selected",selected).apply()
    }
    fun addHistory(line:String) {
        history=(listOf(line)+history).take(100)
        val a=JSONArray(); history.forEach{a.put(it)}; prefs.edit().putString("history",a.toString()).apply()
    }
    fun shareText(text:String) {
        val i=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text); putExtra(Intent.EXTRA_TITLE,"Bihar Jamin Calculator") }
        ctx.startActivity(Intent.createChooser(i,t("हिसाब शेयर करें","Share calculation")))
    }
    fun exportPdf(text:String) {
        val doc=PdfDocument(); val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
        val paint=Paint().apply{ textSize=16f; isAntiAlias=true }
        val lines=text.replace("•","-").split("\n")
        var y=60f
        page.canvas.drawText("Bihar Jamin Calculator",40f,y,paint); y+=35f
        lines.forEach { line -> if(y<800f){ page.canvas.drawText(line.take(75),40f,y,paint); y+=26f } }
        doc.finishPage(page)
        val dir=File(ctx.cacheDir,"shared").apply{mkdirs()}
        val file=File(dir,"Jamin_Report.pdf")
        runCatching { file.outputStream().use{doc.writeTo(it)} }
        doc.close()
        runCatching {
            val uri:Uri=FileProvider.getUriForFile(ctx,"${ctx.packageName}.provider",file)
            val intent=Intent(Intent.ACTION_SEND).apply{type="application/pdf"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            ctx.startActivity(Intent.createChooser(intent,t("PDF रिपोर्ट शेयर करें","Share PDF report")))
        }
    }
    val currentSummary = "${t("प्रोफाइल","Profile")}: $selected\n${t("नाम","Name")}: $plotName\n${t("मालिक","Owner")}: $owner\n$result\n${t("माप आधार","Measurement basis")}: 1 Katha=$ks sq ft; 1 Dhur=$ds sq ft; 1 Decimal=$decs sq ft"

    Scaffold(topBar={ TopAppBar(title={Text(t("बिहार जमीन कैलकुलेटर","Bihar Land Calculator"),fontWeight=FontWeight.Bold)}, actions={
        TextButton(onClick={english=!english;prefs.edit().putBoolean("english",english).apply()}){Text(if(english)"हिंदी" else "English")}
    }) }) { pad ->
        Column(Modifier.padding(pad).padding(14.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(t("जिला/गांव के अनुसार माप चुनें। आप अपना नया माप भी बना सकते हैं।","Choose a district/village measurement profile or create your own."),style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(t("कैलकुलेटर","Calculator"),t("माप सेटिंग","Profiles"),t("हिस्ट्री","History")).forEachIndexed{i,label->
                    FilterChip(selected=tab==i,onClick={tab=i},label={Text(label)})
                }
            }
            when(tab) {
                0 -> {
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("जमीन की जानकारी","Land details"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        OutlinedTextField(plotName,{plotName=it},label={Text(t("प्लॉट/जमीन का नाम (वैकल्पिक)","Plot/Land name (optional)"))},modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(owner,{owner=it},label={Text(t("मालिक का नाम (वैकल्पिक)","Owner name (optional)"))},modifier=Modifier.fillMaxWidth())
                        Text("${t("वर्तमान प्रोफाइल","Active profile")}: $selected",style=MaterialTheme.typography.bodySmall)
                    } }
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("कट्ठा + धुर से डिसमिल","Katha + Dhur to Decimal"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        NumField(t("कट्ठा","Katha"),katha,{katha=it}); NumField(t("धुर","Dhur"),dhur,{dhur=it})
                        Button(onClick={
                            if(valid) {
                                val sq=n(katha)*ks+n(dhur)*ds
                                result="${f(sq/decs)} ${t("डिसमिल","Decimal")} • ${f(sq)} ${t("वर्ग फीट","sq ft")}"
                                addHistory("$plotName | $owner | $katha Katha $dhur Dhur = ${f(sq/decs)} Decimal [$selected; K=$ks,D=$ds,Dec=$decs]")
                            } else result=t("माप सेटिंग में सही मान भरें","Enter valid measurement values in Profiles")
                        },modifier=Modifier.fillMaxWidth()){Text(t("डिसमिल निकालें","Convert to Decimal"))}
                    } }
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("डिसमिल से कट्ठा + धुर","Decimal to Katha + Dhur"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        NumField(t("डिसमिल","Decimal"),decimal,{decimal=it})
                        Button(onClick={
                            if(valid) {
                                val totalD=n(decimal)*decs/ds; val dhurPerK=ks/ds
                                val wholeK=floor(totalD/dhurPerK).toInt(); val rem=totalD-wholeK*dhurPerK
                                result="$wholeK ${t("कट्ठा","Katha")} ${f(rem)} ${t("धुर","Dhur")}"
                                addHistory("$plotName | $owner | $decimal Decimal = $result [$selected; K=$ks,D=$ds,Dec=$decs]")
                            } else result=t("माप सेटिंग में सही मान भरें","Enter valid measurement values in Profiles")
                        },modifier=Modifier.fillMaxWidth()){Text(t("कट्ठा और धुर निकालें","Convert to Katha + Dhur"))}
                    } }
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("लंबाई × चौड़ाई (फीट)","Length × Width (feet)"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        NumField(t("लंबाई फीट","Length in feet"),length,{length=it}); NumField(t("चौड़ाई फीट","Width in feet"),width,{width=it})
                        Button(onClick={
                            if(valid && n(length)>0 && n(width)>0) {
                                val sq=n(length)*n(width); result="${f(sq/decs)} ${t("डिसमिल","Decimal")} • ${f(sq)} sq ft"
                                addHistory("$plotName | $length × $width ft = $result [$selected]")
                            } else result=t("लंबाई, चौड़ाई और माप सही भरें","Enter valid length, width and profile values")
                        },modifier=Modifier.fillMaxWidth()){Text(t("क्षेत्रफल निकालें","Calculate area"))}
                    } }
                    if(result.isNotBlank()) Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("परिणाम","Result"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(result,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={shareText(currentSummary)},modifier=Modifier.weight(1f)){Text(t("शेयर","Share"))}
                            Button(onClick={exportPdf(currentSummary)},modifier=Modifier.weight(1f)){Text(t("PDF रिपोर्ट","PDF Report"))}
                        }
                    } }
                }
                1 -> {
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("जिला/गांव प्रोफाइल","District/Village profiles"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        profiles.forEach { p ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(p.name,fontWeight=FontWeight.SemiBold); Text("Katha ${p.katha} • Dhur ${p.dhur} • Decimal ${p.decimal}",style=MaterialTheme.typography.bodySmall) }
                                TextButton(onClick={selected=p.name;profileName=p.name;kathaSq=p.katha.toString();dhurSq=p.dhur.toString();decimalSq=p.decimal.toString();persistProfiles()}){Text(t("चुनें","Use"))}
                            }
                            HorizontalDivider()
                        }
                        Text(t("नया/अपडेट प्रोफाइल","New / Update profile"),fontWeight=FontWeight.Bold)
                        OutlinedTextField(profileName,{profileName=it},label={Text(t("जिला/गांव का नाम","District/Village name"))},modifier=Modifier.fillMaxWidth())
                        NumField(t("1 कट्ठा = वर्ग फीट","1 Katha = sq ft"),kathaSq,{kathaSq=it})
                        NumField(t("1 धुर = वर्ग फीट","1 Dhur = sq ft"),dhurSq,{dhurSq=it})
                        NumField(t("1 डिसमिल = वर्ग फीट","1 Decimal = sq ft"),decimalSq,{decimalSq=it})
                        Button(onClick={
                            if(profileName.isNotBlank() && n(kathaSq)>0 && n(dhurSq)>0 && n(decimalSq)>0) {
                                val p=LandProfile(profileName.trim(),ks,ds,decs)
                                profiles=(profiles.filterNot{it.name.equals(p.name,true)}+p)
                                selected=p.name; persistProfiles()
                            }
                        },modifier=Modifier.fillMaxWidth()){Text(t("प्रोफाइल सेव करें","Save profile"))}
                        Text(t("नोट: हर जिले/गांव का माप एक जैसा मानकर न चलें। स्थानीय अमीन/सरकारी रिकॉर्ड से पुष्टि करके ही मान सेव करें।","Note: Do not assume all villages in a district use identical units. Verify locally before saving values."),style=MaterialTheme.typography.bodySmall)
                    } }
                }
                else -> {
                    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(t("सेव किए गए हिसाब","Saved calculations"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        if(history.isEmpty()) Text(t("अभी कोई हिसाब नहीं है।","No calculations saved yet.")) else history.forEach { Text("• $it") }
                        OutlinedButton(onClick={history=emptyList();prefs.edit().putString("history","[]").apply()},modifier=Modifier.fillMaxWidth()){Text(t("हिस्ट्री साफ करें","Clear history"))}
                    } }
                }
            }
            Text(t("स्थानीय माप अलग हो सकता है। कानूनी सीमा के लिए सरकारी रिकॉर्ड और अधिकृत अमीन की स्थल-माप को प्राथमिकता दें।","Local land units can vary. For legal boundaries, rely on official records and an authorized surveyor."),style=MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = "Developed by Sami Sajjad",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
