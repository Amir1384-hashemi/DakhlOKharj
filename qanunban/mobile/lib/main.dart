import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'official_feed.dart';

const navy = Color(0xFF132F54);
const teal = Color(0xFF098C83);

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const QanunBanApp());
}

class QanunBanApp extends StatelessWidget {
  const QanunBanApp({super.key, this.fetcher = fetchOfficialNews});
  final Future<List<OfficialNews>> Function() fetcher;
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'قانون‌بان',
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor:navy),
      scaffoldBackgroundColor:const Color(0xFFF3F6FA),
      useMaterial3:true,
    ),
    builder:(context,child) => Directionality(
      textDirection:TextDirection.rtl,
      child:child ?? const SizedBox.shrink(),
    ),
    home:NewsHome(fetcher:fetcher),
  );
}

class NewsHome extends StatefulWidget {
  const NewsHome({super.key,required this.fetcher});
  final Future<List<OfficialNews>> Function() fetcher;
  @override
  State<NewsHome> createState() => _NewsHomeState();
}

class _NewsHomeState extends State<NewsHome> {
  final search = TextEditingController();
  List<OfficialNews> docs = [];
  Set<String> known = {};
  Set<String> fresh = {};
  bool baseline = false;
  bool loading = false;
  String error = '';
  String category = 'همه';
  int tab = 0;
  DateTime? lastChecked;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final ids = prefs.getStringList('previous_ids');
      if (ids != null) {known=ids.toSet(); baseline=true;}
    } catch (_) {
      // The app must still open if on-device preferences are unavailable.
    }
    await refresh();
  }

  Future<void> refresh() async {
    if (loading) return;
    if (mounted) setState(() {loading=true;error='';});
    try {
      final got = await widget.fetcher();
      final ids = got.map((e)=>e.id).toSet();
      final diff = baseline ? ids.difference(known) : <String>{};
      known.addAll(ids);
      baseline=true;
      try {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setStringList('previous_ids',known.take(250).toList());
      } catch (_) {/* Allow viewing the feed even without persistent storage. */}
      if (!mounted) return;
      setState(() {
        docs=got;
        fresh.addAll(diff);
        lastChecked=DateTime.now();
      });
      if (diff.isNotEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content:Text(diff.length.toString()+' خبر جدید نسبت به بررسی قبلی پیدا شد.')),
        );
      }
    } catch (e) {
      if (mounted) setState(()=>error='دریافت از منبع رسمی ناموفق بود: '+e.toString());
    } finally {
      if (mounted) setState(()=>loading=false);
    }
  }

  Future<void> openItem(OfficialNews item) async {
    final uri=Uri.tryParse(item.url);
    if (uri == null || uri.scheme != 'https' || uri.host != 'dotic.ir') return;
    try {
      final ok=await launchUrl(uri,mode:LaunchMode.externalApplication);
      if (!ok) throw StateError('مرورگر قابل دسترس نیست.');
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content:Text('بازکردن خبر ناموفق بود: '+e.toString())),
      );
    }
  }

  @override
  void dispose(){search.dispose();super.dispose();}

  Widget newsPage() {
    final q=search.text.trim();
    final matches=docs.where((d) =>
      (category == 'همه' || d.category == category) &&
      (q.isEmpty || d.title.contains(q) || d.category.contains(q))).toList();
    return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      const Text('آخرین خبرهای مقرراتی منتشرشده',
        style:TextStyle(fontSize:18,fontWeight:FontWeight.bold)),
      const SizedBox(height:8),
      const Text('این موارد خبرهای منتشرشده‌اند؛ به معنی اعتبار یا لازم‌الاجراشدن قانون نیستند.',
        style:TextStyle(color:Colors.black54,fontSize:12)),
      const SizedBox(height:14),
      TextField(controller:search,onChanged:(_)=>setState((){}),
        decoration:const InputDecoration(
          prefixIcon:Icon(Icons.search),
          labelText:'جست‌وجوی خبر',
          border:OutlineInputBorder())),
      const SizedBox(height:10),
      DropdownButtonFormField<String>(
        initialValue:category,
        isExpanded:true,
        decoration:const InputDecoration(labelText:'حوزه',border:OutlineInputBorder()),
        items:const ['همه','مالی و مالیاتی','کار و بیمه','حقوقی و قضایی',
          'بانک و تجارت','سایر مقررات']
          .map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),
        onChanged:(x)=>setState(()=>category=x ?? 'همه')),
      const SizedBox(height:12),
      if(matches.isEmpty && error.isEmpty && !loading)
        const Card(child:Padding(padding:EdgeInsets.all(18),
          child:Text('موردی یافت نشد. برای دریافت خبرهای تازه، به‌روزرسانی کنید.'))),
      ...matches.map((d)=>Card(
        child:ListTile(
          title:Text(d.title,style:const TextStyle(fontWeight:FontWeight.w600)),
          subtitle:Text(d.category+' | منبع: dotic.ir'),
          trailing:Icon(fresh.contains(d.id)?Icons.fiber_new:Icons.open_in_new,
            color:fresh.contains(d.id)?teal:Colors.black45),
          onTap:()=>openItem(d),
        ),
      )),
    ]);
  }

  Widget newPage() => Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
    const Text('تازه‌ها از آخرین بررسی',
      style:TextStyle(fontSize:18,fontWeight:FontWeight.bold)),
    const SizedBox(height:8),
    const Text('فقط هنگام بازکردن یا به‌روزرسانی برنامه بررسی می‌شود. هشدار پس‌زمینه هنوز فعال نیست.'),
    const SizedBox(height:12),
    if (fresh.isEmpty) const Card(child:Padding(padding:EdgeInsets.all(18),
      child:Text('هنوز خبر تازه‌ای نسبت به بررسی قبلی پیدا نشده است.'))),
    ...docs.where((d)=>fresh.contains(d.id)).map((d)=>Card(
      child:ListTile(title:Text(d.title),subtitle:Text(d.category),onTap:()=>openItem(d)))),
  ]);

  Widget infoPage() => Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
    Image.asset('assets/icon.png',height:96),
    const SizedBox(height:14),
    const Text('قانون‌بان',textAlign:TextAlign.center,
      style:TextStyle(fontSize:24,fontWeight:FontWeight.bold,color:navy)),
    const SizedBox(height:8),
    const Text('دریافت مستقیم خبرهای مقررات از منبع رسمی',textAlign:TextAlign.center),
    const SizedBox(height:16),
    Card(child:ListTile(title:const Text('آخرین بررسی موفق'),
      subtitle:Text(lastChecked?.toLocal().toString().substring(0,16) ?? 'هنوز انجام نشده'))),
    Card(child:ListTile(title:const Text('تعداد خبرهای دریافت‌شده'),
      subtitle:Text(docs.length.toString()))),
    const SizedBox(height:12),
    FilledButton.icon(onPressed:loading?null:refresh,icon:const Icon(Icons.refresh),
      label:const Text('دریافت تازه‌ترین خبرها')),
    const SizedBox(height:12),
    const Text('توجه: این نسخه، متن کامل قوانین را تحلیل نمی‌کند و هنگام بسته‌بودن اعلان نمی‌فرستد. برای پایش ۲۴ ساعته، سرور لازم است.',
      style:TextStyle(fontSize:12,color:Colors.black54)),
  ]);

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar:AppBar(
      title:Row(mainAxisSize:MainAxisSize.min,children:[
        Image.asset('assets/icon.png',width:32,height:32),
        const SizedBox(width:8),const Text('قانون‌بان'),
      ]),
      actions:[IconButton(onPressed:loading?null:refresh,
        tooltip:'به‌روزرسانی',icon:const Icon(Icons.refresh))],
    ),
    body:SafeArea(child:Center(child:ConstrainedBox(
      constraints:const BoxConstraints(maxWidth:780),
      child:RefreshIndicator(onRefresh:refresh,child:ListView(
        padding:const EdgeInsets.all(16),children:[
          if(loading)const LinearProgressIndicator(),
          if(error.isNotEmpty)Card(color:Colors.red.shade50,child:Padding(
            padding:const EdgeInsets.all(12),
            child:Text(error,style:const TextStyle(color:Colors.red)))),
          const SizedBox(height:10),
          if(tab == 0)newsPage() else if(tab == 1)newPage() else infoPage(),
        ],
      )),
    ))),
    bottomNavigationBar:NavigationBar(selectedIndex:tab,
      onDestinationSelected:(index)=>setState(()=>tab=index),
      destinations:const [
        NavigationDestination(icon:Icon(Icons.article_outlined),label:'مقررات'),
        NavigationDestination(icon:Icon(Icons.notifications_outlined),label:'تازه‌ها'),
        NavigationDestination(icon:Icon(Icons.settings_outlined),label:'درباره'),
      ]),
  );
}
