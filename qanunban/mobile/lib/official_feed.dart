import 'dart:async';
import 'dart:convert';

import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as parser;
import 'package:http/http.dart' as http;

const officialHome = 'https://dotic.ir/';
const _officialHosts = <String>{'dotic.ir', 'www.dotic.ir'};

class OfficialNews {
  const OfficialNews({
    required this.id,
    required this.title,
    required this.url,
    required this.category,
    this.publishedAt,
    this.summary = '',
  });

  final String id;
  final String title;
  final String url;
  final String category;
  final DateTime? publishedAt;
  final String summary;

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'url': url,
    'category': category,
    'publishedAt': publishedAt?.toIso8601String(),
    'summary': summary,
  };

  factory OfficialNews.fromJson(Map<String, dynamic> json) => OfficialNews(
    id: json['id']?.toString() ?? '',
    title: json['title']?.toString() ?? '',
    url: json['url']?.toString() ?? '',
    category: json['category']?.toString() ?? classifyNews(
      json['title']?.toString() ?? '',
    ),
    publishedAt: DateTime.tryParse(json['publishedAt']?.toString() ?? ''),
    summary: json['summary']?.toString() ?? '',
  );
}

String classifyNews(String title) {
  const labels = <String, List<String>>{
    'مالی و مالیاتی': ['مالیات', 'مالیاتی', 'ارزش افزوده', 'گمرک', 'بودجه', 'عوارض'],
    'کار و بیمه': ['بیمه', 'بازنشستگی', 'کارگر', 'دستمزد', 'تأمین اجتماعی', 'تامین اجتماعی', 'قانون کار'],
    'حقوقی و قضایی': ['دیوان', 'قضایی', 'دادگاه', 'رأی', 'رای', 'عدالت اداری', 'آیین دادرسی'],
    'بانک و تجارت': ['بانک', 'بورس', 'تجارت', 'اعتباری', 'ارزی', 'صادرات', 'واردات'],
  };
  var match = 'سایر مقررات';
  var count = 0;
  for (final item in labels.entries) {
    final hits = item.value.where((word) => title.contains(word)).length;
    if (hits > count) {
      count = hits;
      match = item.key;
    }
  }
  return match;
}

String _clean(String value) => value.replaceAll(RegExp(r'\s+'), ' ').trim();

Uri? _officialUri(String href, [Uri? base]) {
  try {
    final uri = (base ?? Uri.parse(officialHome)).resolve(href);
    if (uri.scheme != 'https' || !_officialHosts.contains(uri.host.toLowerCase())) {
      return null;
    }
    return uri;
  } catch (_) {
    return null;
  }
}

String? _documentId(Uri uri) {
  for (final key in ['id', 'newsId', 'documentId', 'regulationId', 'itemId']) {
    final value = uri.queryParameters[key];
    if (value != null && RegExp(r'^[0-9]{2,}$').hasMatch(value)) return value;
  }
  final patterns = <RegExp>[
    RegExp(r'/(?:news|document|documents|law|laws|regulation|regulations|detail|view)/([0-9]{2,})(?:/|$)'),
    RegExp(r'/([0-9]{3,})(?:/|$)'),
  ];
  for (final pattern in patterns) {
    final match = pattern.firstMatch(uri.path);
    if (match != null) return match.group(1);
  }
  // Some official feeds use a slug URL rather than a numeric document id.
  if (uri.pathSegments.length >= 2) {
    final path = uri.path.replaceAll(RegExp(r'/+$'), '');
    if (path.length > 2 && !path.contains('/category/')) {
      return path + (uri.hasQuery ? '?'+uri.queryParameters.entries
          .where((e) => e.key != 'utm_source' && e.key != 'utm_medium')
          .map((e) => e.key+'='+e.value).join('&') : '');
    }
  }
  return null;
}

DateTime? _dateFrom(dom.Element? element) {
  if (element == null) return null;
  final value = element.attributes['datetime'] ??
      element.attributes['content'] ??
      element.text.trim();
  return DateTime.tryParse(value);
}

String _titleFor(dom.Element anchor) {
  final direct = _clean(anchor.text);
  if (direct.length >= 6 && direct.length <= 650) return direct;
  final container = anchor.closest('article, li, .card, .news-item, .document-item, .item');
  final heading = container?.querySelector('h1, h2, h3, h4, .title, .news-title');
  return _clean(heading?.text ?? direct);
}

List<OfficialNews> parseOfficialNews(String htmlBody, {Uri? baseUri}) {
  final doc = parser.parse(htmlBody);
  final base = baseUri ?? Uri.parse(officialHome);
  final found = <OfficialNews>[];
  final ids = <String>{};
  for (final anchor in doc.querySelectorAll('a[href]')) {
    final href = anchor.attributes['href'];
    if (href == null || href.trim().isEmpty) continue;
    final uri = _officialUri(href, base);
    if (uri == null) continue;
    final id = _documentId(uri);
    if (id == null || !ids.add(id)) continue;

    final title = _titleFor(anchor);
    if (title.length < 6 || title.length > 650) {
      ids.remove(id);
      continue;
    }
    final container = anchor.closest('article, li, .card, .news-item, .document-item, .item');
    final time = container?.querySelector('time, [datetime], .date, .published-at');
    final summaryElement = container?.querySelector('p, .summary, .description, .excerpt');
    final summary = _clean(summaryElement?.text ?? '');
    found.add(OfficialNews(
      id: id,
      title: title,
      url: uri.toString(),
      category: classifyNews(title),
      publishedAt: _dateFrom(time),
      summary: summary == title ? '' : summary,
    ));
    if (found.length >= 100) break;
  }
  return found;
}

List<OfficialNews> parseSyndicationFeed(String body, {Uri? baseUri}) {
  final doc = parser.parse(body);
  final base = baseUri ?? Uri.parse(officialHome);
  final found = <OfficialNews>[];
  final ids = <String>{};
  final entries = doc.querySelectorAll('item, entry');
  for (final entry in entries) {
    final title = _clean(entry.querySelector('title')?.text ?? '');
    if (title.length < 6 || title.length > 650) continue;
    final linkElement = entry.querySelector('link');
    final href = linkElement?.attributes['href'] ?? linkElement?.text.trim() ??
        entry.querySelector('guid, id')?.text.trim();
    if (href == null || href.isEmpty) continue;
    final uri = _officialUri(href, base);
    if (uri == null) continue;
    final id = _documentId(uri) ?? uri.path;
    if (!ids.add(id)) continue;
    final dateElement = entry.querySelector('pubDate, published, updated, dc\\:date');
    final summary = _clean(entry.querySelector('description, summary, content')?.text ?? '');
    found.add(OfficialNews(
      id: id,
      title: title,
      url: uri.toString(),
      category: classifyNews(title),
      publishedAt: _dateFrom(dateElement),
      summary: summary,
    ));
    if (found.length >= 100) break;
  }
  return found;
}

Future<String> _download(http.Client client, Uri uri) async {
  final request = http.Request('GET', uri)
    ..headers.addAll({
      'Accept': 'text/html,application/rss+xml,application/atom+xml,application/xml;q=0.9,*/*;q=0.8',
      'User-Agent': 'QanunBan/1.1 (regulatory news reader; contact: support@qanunban.app)',
      'Accept-Language': 'fa-IR,fa;q=0.9,en;q=0.7',
    });
  final response = await client.send(request).timeout(const Duration(seconds: 15));
  if (response.statusCode != 200) {
    await response.stream.drain<void>();
    throw StateError('HTTP '+response.statusCode.toString()+' از '+uri.host);
  }
  final bytes = await response.stream.toBytes().timeout(const Duration(seconds: 15));
  if (bytes.length > 4 * 1024 * 1024) {
    throw StateError('حجم پاسخ منبع از حد مجاز بیشتر است.');
  }
  return utf8.decode(bytes, allowMalformed: true);
}

Future<List<OfficialNews>> fetchOfficialNews() async {
  final client = http.Client();
  final failures = <String>[];
  try {
    try {
      final html = await _download(client, Uri.parse(officialHome));
      final results = parseOfficialNews(html);
      if (results.isNotEmpty) return results;
      failures.add('صفحه اصلی دریافت شد اما لینک خبر قابل تشخیص نبود');
      final document = parser.parse(html);
      final rssLinks = document.querySelectorAll('link[rel="alternate"][href]')
          .map((e) => e.attributes['href'])
          .whereType<String>()
          .where((href) => href.toLowerCase().contains('rss') ||
              href.toLowerCase().contains('atom') ||
              href.toLowerCase().contains('feed'))
          .map((href) => Uri.parse(officialHome).resolve(href))
          .where((uri) => _officialHosts.contains(uri.host.toLowerCase()));
      for (final uri in rssLinks) {
        try {
          final body = await _download(client, uri);
          final items = parseSyndicationFeed(body, baseUri: uri);
          if (items.isNotEmpty) return items;
        } catch (e) {
          failures.add(e.toString());
        }
      }
    } catch (e) {
      failures.add(e.toString());
    }

    // Try common public syndication endpoints if the homepage has no feed
    // link or is temporarily unavailable. All requests remain on dotic.ir.
    for (final path in ['/rss', '/rss.xml', '/feed', '/news/rss', '/rss/news', '/feed.xml']) {
      final uri = Uri.parse(officialHome).resolve(path);
      try {
        final body = await _download(client, uri);
        final items = parseSyndicationFeed(body, baseUri: uri);
        if (items.isNotEmpty) return items;
      } catch (e) {
        failures.add(e.toString());
      }
    }
    throw StateError(
      'خبر قابل دریافت از منبع رسمی پیدا نشد. ممکن است سایت محدودیت دسترسی داشته باشد یا ساختار آن تغییر کرده باشد. '
      + failures.take(3).join('؛ '),
    );
  } finally {
    client.close();
  }
}
