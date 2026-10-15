import 'dart:async';
import 'dart:convert';
import 'package:html/parser.dart' as parser;
import 'package:http/http.dart' as http;

const officialHome = 'https://dotic.ir/';

class OfficialNews {
  const OfficialNews({required this.id, required this.title, required this.url, required this.category});
  final String id;
  final String title;
  final String url;
  final String category;
}

String classifyNews(String title) {
  const labels = {
    'مالی و مالیاتی': ['مالیات','مالیاتی','ارزش افزوده','گمرک','بودجه'],
    'کار و بیمه': ['بیمه','بازنشستگی','کارگر','دستمزد','تأمین اجتماعی'],
    'حقوقی و قضایی': ['دیوان','قضایی','دادگاه','رأی','عدالت اداری'],
    'بانک و تجارت': ['بانک','بورس','تجارت','اعتباری','ارزی']
  };
  var match = 'سایر مقررات';
  var count = 0;
  for (final item in labels.entries) {
    final hits = item.value.where((w) => title.contains(w)).length;
    if (hits > count) {count = hits; match = item.key;}
  }
  return match;
}

List<OfficialNews> parseOfficialNews(String htmlBody) {
  final doc = parser.parse(htmlBody);
  final found = <OfficialNews>[];
  final ids = <String>{};
  for (final anchor in doc.querySelectorAll('a[href]')) {
    final href = anchor.attributes['href'];
    if (href == null) continue;
    final Uri uri;
    try {uri = Uri.parse(officialHome).resolve(href);} catch (_) {continue;}
    if (uri.scheme != 'https' || !{'dotic.ir','www.dotic.ir'}.contains(uri.host.toLowerCase())) continue;
    final m = RegExp(r'^/news/(\d+)/?$').firstMatch(uri.path);
    final title = anchor.text.trim().replaceAll(RegExp(r'\s+'),' ');
    if (m == null || title.length < 6 || title.length > 650) continue;
    final id = m.group(1)!;
    if (!ids.add(id)) continue;
    found.add(OfficialNews(id:id, title:title, url:'https://dotic.ir/news/$id',category:classifyNews(title)));
    if (found.length >= 50) break;
  }
  return found;
}

Future<List<OfficialNews>> fetchOfficialNews() async {
  final client = http.Client();
  try {
    final request = http.Request('GET', Uri.parse(officialHome))
      ..headers['Accept'] = 'text/html; charset=utf-8';
    final streamed = await client.send(request).timeout(const Duration(seconds:20));
    if (streamed.statusCode != 200) {
      throw StateError('منبع رسمی پاسخ HTTP '+streamed.statusCode.toString()+' داد.');
    }
    final bytes = await streamed.stream.toBytes().timeout(const Duration(seconds:15));
    if (bytes.length > 2500000) throw StateError('حجم صفحه منبع بیش از حد مجاز است.');
    final items = parseOfficialNews(utf8.decode(bytes,allowMalformed:true));
    if (items.isEmpty) throw StateError('در صفحه فعلی منبع رسمی، لینک خبری قابل دریافت پیدا نشد.');
    return items;
  } finally {
    client.close();
  }
}
