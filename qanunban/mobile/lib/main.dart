import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:url_launcher/url_launcher.dart';

import 'article_analyzer.dart';
import 'local_store.dart';
import 'official_feed.dart';

const navy = Color(0xFF132F54);
const teal = Color(0xFF098C83);
const allCategories = <String>[
  'همه',
  ...defaultWatchedCategories,
];

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
      colorScheme: ColorScheme.fromSeed(seedColor: navy),
      scaffoldBackgroundColor: const Color(0xFFF3F6FA),
      useMaterial3: true,
      cardTheme: const CardThemeData(
        elevation: 0,
        margin: EdgeInsets.only(bottom: 9),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(14)),
        ),
      ),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
        filled: true,
        fillColor: Colors.white,
      ),
    ),
    builder: (context, child) => Directionality(
      textDirection: TextDirection.rtl,
      child: child ?? const SizedBox.shrink(),
    ),
    home: NewsHome(fetcher: fetcher),
  );
}

class NewsHome extends StatefulWidget {
  const NewsHome({super.key, required this.fetcher});
  final Future<List<OfficialNews>> Function() fetcher;

  @override
  State<NewsHome> createState() => _NewsHomeState();
}

class _NewsHomeState extends State<NewsHome> with WidgetsBindingObserver {
  final _search = TextEditingController();
  final _savedSearch = TextEditingController();
  final _articleInput = TextEditingController();
  final FlutterLocalNotificationsPlugin _notifications =
      FlutterLocalNotificationsPlugin();

  QanunBanStore? _store;
  List<OfficialNews> _documents = [];
  Set<String> _knownIds = {};
  Set<String> _freshIds = {};
  Set<String> _savedIds = {};
  Set<String> _watchedCategories = defaultWatchedCategories.toSet();
  List<AlertRecord> _alerts = [];
  ArticleAnalysis? _analysis;
  String _categoryFilter = 'همه';
  String _error = '';
  bool _loading = false;
  bool _notificationsEnabled = true;
  bool _notificationsReady = false;
  bool _baselineInitialized = false;
  int _tab = 0;
  DateTime? _lastChecked;
  Timer? _autoRefreshTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    try {
      final local = await QanunBanStore.open();
      _store = local;
      _documents = local.cachedDocuments;
      _knownIds = local.knownIds;
      _baselineInitialized = local.hasBaseline;
      _savedIds = local.savedIds;
      _watchedCategories = local.watchedCategories;
      _alerts = local.alerts;
      _notificationsEnabled = local.notificationsEnabled;
      _lastChecked = local.lastChecked;
      _freshIds = _alerts
          .where((a) => DateTime.now().difference(a.createdAt).inHours < 24)
          .map((a) => a.id)
          .toSet();
      if (mounted) setState(() {});
    } catch (_) {
      // The application remains usable even if local storage is unavailable.
    }

    await _initializeNotifications();
    if (!mounted) return;
    _autoRefreshTimer = Timer.periodic(const Duration(minutes: 30), (_) {
      if (mounted && !_loading) refresh();
    });
    await refresh();
  }

  Future<void> _initializeNotifications() async {
    try {
      await _notifications.initialize(
        const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        ),
      );
      final android = _notifications.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();
      await android?.requestNotificationsPermission();
      _notificationsReady = true;
    } catch (_) {
      // In-app alert history still works if OS notifications are unavailable.
      _notificationsReady = false;
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        !_loading &&
        (_lastChecked == null ||
            DateTime.now().difference(_lastChecked!).inMinutes >= 15)) {
      refresh();
    }
  }

  Future<void> refresh() async {
    if (_loading) return;
    if (mounted) setState(() {
      _loading = true;
      _error = '';
    });

    try {
      final fetched = await widget.fetcher();
      if (fetched.isEmpty) {
        throw StateError('منبع رسمی فهرست خالی برگرداند؛ داده‌های ذخیره‌شده حفظ شدند.');
      }

      final firstBaseline = !(_store?.hasBaseline ?? _baselineInitialized);
      final newDocuments = firstBaseline
          ? <OfficialNews>[]
          : fetched.where((d) => !_knownIds.contains(d.id)).toList();

      _knownIds.addAll(fetched.map((d) => d.id));
      _baselineInitialized = true;
      final now = DateTime.now();
      final matchingNew = newDocuments
          .where((d) => _watchedCategories.contains(d.category))
          .toList();
      final records = matchingNew.map((d) => AlertRecord(
        id: d.id,
        title: d.title,
        url: d.url,
        category: d.category,
        createdAt: now,
      )).toList();

      final local = _store;
      if (local != null) {
        await local.cacheDocuments(fetched);
        await local.setKnownIds(_knownIds);
        await local.setLastChecked(now);
        if (records.isNotEmpty) await local.addAlerts(records);
      }

      if (!mounted) return;
      setState(() {
        _documents = fetched;
        _freshIds = records.map((a) => a.id).toSet();
        _lastChecked = now;
        if (local != null) _alerts = local.alerts;
      });

      if (records.isNotEmpty && mounted) {
        if (_notificationsEnabled && _notificationsReady) {
          for (var i = 0; i < records.length; i++) {
            final alert = records[i];
            try {
              await _notifications.show(
                (now.millisecondsSinceEpoch + i) & 0x7fffffff,
                'قانون‌بان: خبر مقرراتی تازه',
                alert.title,
                const NotificationDetails(
                  android: AndroidNotificationDetails(
                    'qanunban_regulatory_alerts',
                    'هشدارهای مقرراتی',
                    channelDescription: 'خبرهای تازه در حوزه‌های انتخابی شما',
                    importance: Importance.high,
                    priority: Priority.high,
                    icon: '@mipmap/ic_launcher',
                  ),
                ),
                payload: alert.url,
              );
            } catch (_) {
              // The alert is still saved in the in-app alert center.
            }
          }
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(
            records.length.toString() + ' خبر جدید در حوزه‌های انتخابی شما ثبت شد.',
          )),
        );
      }
    } catch (e) {
      if (mounted) setState(() {
        _error = e.toString().replaceFirst('Bad state: ', '');
      });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _toggleSaved(OfficialNews item) async {
    final local = _store;
    if (local == null) {
      _showMessage('ذخیره‌سازی روی این دستگاه در دسترس نیست.');
      return;
    }
    final saved = await local.toggleSaved(item.id);
    if (mounted) setState(() => _savedIds = saved);
  }

  Future<void> _openOfficialUrl(String rawUrl) async {
    final uri = Uri.tryParse(rawUrl);
    if (uri == null ||
        uri.scheme != 'https' ||
        !{'dotic.ir', 'www.dotic.ir'}.contains(uri.host.toLowerCase())) {
      _showMessage('پیوند معتبر منبع رسمی نیست.');
      return;
    }
    try {
      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok) throw StateError('مرورگر قابل دسترس نیست.');
    } catch (e) {
      _showMessage('بازکردن پیوند ناموفق بود: ' + e.toString());
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _showDocumentDetails(OfficialNews item) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(item.title, style: const TextStyle(
                  fontSize: 19, fontWeight: FontWeight.bold, color: navy,
                )),
                const SizedBox(height: 10),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  Chip(label: Text(item.category)),
                  if (item.publishedAt != null)
                    Chip(label: Text('تاریخ: ' + _formatDate(item.publishedAt!))),
                  const Chip(label: Text('منبع: dotic.ir')),
                ]),
                if (item.summary.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(item.summary, style: const TextStyle(fontSize: 15)),
                ],
                const SizedBox(height: 14),
                const Text(
                  'این رکورد از صفحه منبع دریافت شده است؛ به‌تنهایی اثبات نمی‌کند که متن، قانون لازم‌الاجرا یا آخرین نسخه معتبر آن است.',
                  style: TextStyle(fontSize: 12, color: Colors.black54),
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: () {
                    Navigator.of(sheetContext).pop();
                    _openOfficialUrl(item.url);
                  },
                  icon: const Icon(Icons.open_in_browser),
                  label: const Text('بازکردن سند در منبع رسمی'),
                ),
                OutlinedButton.icon(
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: item.url));
                    if (sheetContext.mounted) Navigator.of(sheetContext).pop();
                    _showMessage('پیوند سند کپی شد.');
                  },
                  icon: const Icon(Icons.link),
                  label: const Text('کپی پیوند سند'),
                ),
                OutlinedButton.icon(
                  onPressed: () async {
                    await _toggleSaved(item);
                    if (sheetContext.mounted) Navigator.of(sheetContext).pop();
                  },
                  icon: Icon(_savedIds.contains(item.id)
                      ? Icons.bookmark_remove_outlined
                      : Icons.bookmark_add_outlined),
                  label: Text(_savedIds.contains(item.id)
                      ? 'حذف از اسناد ذخیره‌شده'
                      : 'ذخیره برای مراجعه بعدی'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final local = date.toLocal();
    return local.year.toString().padLeft(4, '0') + '/' +
        local.month.toString().padLeft(2, '0') + '/' +
        local.day.toString().padLeft(2, '0');
  }

  Widget _newsCard(OfficialNews item) => Card(
    child: ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
      leading: CircleAvatar(
        backgroundColor: const Color(0xFFE5F2F1),
        child: Icon(
          _freshIds.contains(item.id) ? Icons.fiber_new : Icons.article_outlined,
          color: teal,
        ),
      ),
      title: Text(item.title, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 5),
        child: Text(
          item.category + (item.publishedAt == null
              ? ' • منبع: dotic.ir'
              : ' • ' + _formatDate(item.publishedAt!)),
        ),
      ),
      trailing: IconButton(
        tooltip: _savedIds.contains(item.id) ? 'حذف از ذخیره‌ها' : 'ذخیره سند',
        onPressed: () => _toggleSaved(item),
        icon: Icon(
          _savedIds.contains(item.id) ? Icons.bookmark : Icons.bookmark_border,
          color: _savedIds.contains(item.id) ? teal : Colors.black54,
        ),
      ),
      onTap: () => _showDocumentDetails(item),
    ),
  );

  Widget _feedPage() {
    final query = _search.text.trim();
    final matches = _documents.where((d) =>
      (_categoryFilter == 'همه' || d.category == _categoryFilter) &&
      (query.isEmpty || d.title.contains(query) ||
          d.category.contains(query) || d.summary.contains(query))
    ).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('آخرین خبرهای مقرراتی منتشرشده',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        const SizedBox(height: 7),
        const Text(
          'جست‌وجو در داده‌های دریافت‌شده از منبع؛ خبر به‌تنهایی به معنی لازم‌الاجراشدن قانون نیست.',
          style: TextStyle(color: Colors.black54, fontSize: 12)),
        const SizedBox(height: 14),
        TextField(
          controller: _search,
          onChanged: (_) => setState(() {}),
          textInputAction: TextInputAction.search,
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search),
            labelText: 'جست‌وجوی عنوان و خلاصه',
          ),
        ),
        const SizedBox(height: 10),
        DropdownButtonFormField<String>(
          initialValue: _categoryFilter,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'فیلتر حوزه'),
          items: allCategories.map((value) =>
            DropdownMenuItem(value: value, child: Text(value))).toList(),
          onChanged: (value) => setState(() => _categoryFilter = value ?? 'همه'),
        ),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: Text(
            matches.length.toString() + ' مورد از ' + _documents.length.toString(),
            style: const TextStyle(color: Colors.black54))),
          if (_lastChecked != null)
            Text('آخرین دریافت: ' + _formatDate(_lastChecked!),
              style: const TextStyle(fontSize: 11, color: Colors.black54)),
        ]),
        const SizedBox(height: 8),
        if (_documents.isEmpty && !_loading)
          _emptyCard(
            _error.isNotEmpty
                ? 'داده‌ای از منبع دریافت نشده است. اتصال اینترنت و دسترسی منبع را بررسی کنید.'
                : 'هنوز داده‌ای دریافت نشده؛ دکمه به‌روزرسانی را بزنید.',
            icon: Icons.cloud_off_outlined,
          )
        else if (matches.isEmpty)
          _emptyCard('با این عبارت یا حوزه، موردی پیدا نشد.', icon: Icons.search_off)
        else
          ...matches.map(_newsCard),
      ],
    );
  }

  Widget _alertsPage() {
    final unread = _alerts.where((a) => !a.isRead).length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('مرکز هشدارها',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        const SizedBox(height: 6),
        Text(
          unread.toString() + ' هشدار خوانده‌نشده • هشدارها پس از دریافت موفق داده ثبت می‌شوند.',
          style: const TextStyle(color: Colors.black54, fontSize: 12),
        ),
        const SizedBox(height: 10),
        Wrap(spacing: 8, children: [
          OutlinedButton.icon(
            onPressed: unread == 0 ? null : _markAllAlertsRead,
            icon: const Icon(Icons.done_all),
            label: const Text('خواندن همه'),
          ),
          OutlinedButton.icon(
            onPressed: _alerts.isEmpty ? null : _clearAlerts,
            icon: const Icon(Icons.delete_outline),
            label: const Text('پاک‌کردن تاریخچه'),
          ),
        ]),
        const SizedBox(height: 8),
        if (_alerts.isEmpty)
          _emptyCard('هنوز هشداری ثبت نشده است. حوزه‌های موردنظر را در تنظیمات انتخاب و داده‌ها را به‌روزرسانی کنید.',
            icon: Icons.notifications_none)
        else
          ..._alerts.map((alert) => Card(
            child: ListTile(
              leading: Icon(
                alert.isRead ? Icons.notifications_none : Icons.notifications_active,
                color: alert.isRead ? Colors.black45 : teal,
              ),
              title: Text(alert.title,
                style: TextStyle(fontWeight: alert.isRead ? FontWeight.normal : FontWeight.bold)),
              subtitle: Text(alert.category + ' • ' + _formatDate(alert.createdAt)),
              trailing: const Icon(Icons.open_in_new),
              onTap: () async {
                final local = _store;
                if (local != null) await local.markAlertRead(alert.id);
                if (!mounted) return;
                setState(() => _alerts = local?.alerts ?? _alerts);
                await _openOfficialUrl(alert.url);
              },
            ),
          )),
      ],
    );
  }

  Future<void> _markAllAlertsRead() async {
    final local = _store;
    if (local == null) return;
    await local.markAllAlertsRead();
    if (mounted) setState(() => _alerts = local.alerts);
  }

  Future<void> _clearAlerts() async {
    final local = _store;
    if (local == null) return;
    await local.clearAlerts();
    if (mounted) setState(() => _alerts = []);
  }

  Widget _savedPage() {
    final query = _savedSearch.text.trim();
    final items = _documents.where((d) =>
      _savedIds.contains(d.id) &&
      (query.isEmpty || d.title.contains(query) || d.category.contains(query))
    ).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('اسناد ذخیره‌شده',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        TextField(
          controller: _savedSearch,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search),
            labelText: 'جست‌وجو در ذخیره‌ها',
          ),
        ),
        const SizedBox(height: 12),
        if (_savedIds.isEmpty)
          _emptyCard('برای نگهداری یک سند، نماد نشانک کنار آن را لمس کنید.',
            icon: Icons.bookmark_border)
        else if (items.isEmpty)
          _emptyCard('سند ذخیره‌شده‌ای با این عبارت در فهرست فعلی نیست. پس از دریافت مجدد، اسناد ذخیره‌شده دوباره قابل مشاهده‌اند.',
            icon: Icons.search_off)
        else
          ...items.map(_newsCard),
      ],
    );
  }

  Widget _analysisPage() {
    final result = _analysis;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('تحلیل اولیه ماده یا تبصره',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        const SizedBox(height: 7),
        const Text(
          'متن را از سند رسمی کپی و وارد کنید. این ابزار تعهدها، منع‌ها، اختیارها، استثناها، مهلت‌ها و ارجاعات را با قواعد متنی استخراج می‌کند؛ تحلیل هوش مصنوعی یا نظر حقوقی قطعی نیست.',
          style: TextStyle(color: Colors.black54, fontSize: 12)),
        const SizedBox(height: 12),
        TextField(
          controller: _articleInput,
          minLines: 7,
          maxLines: 14,
          maxLength: 12000,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            alignLabelWithHint: true,
            labelText: 'متن ماده، تبصره یا بند',
            hintText: 'متن دقیق مقرره را اینجا وارد کنید…',
          ),
        ),
        FilledButton.icon(
          onPressed: _articleInput.text.trim().isEmpty
              ? null
              : () => setState(() => _analysis = ArticleAnalyzer.analyze(_articleInput.text)),
          icon: const Icon(Icons.manage_search),
          label: const Text('تحلیل متن واردشده'),
        ),
        if (result != null) ...[
          const SizedBox(height: 14),
          Card(child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text('نتیجه استخراج • ' + result.wordCount.toString() + ' واژه',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 6),
              const Text('استخراج خودکار ممکن است بخشی از معنا یا استثناهای حقوقی را تشخیص ندهد؛ متن اصلی را مبنا قرار دهید.',
                style: TextStyle(fontSize: 12, color: Colors.black54)),
              const Divider(height: 22),
              _analysisGroup('تعهدها و تکالیف', result.obligations),
              _analysisGroup('ممنوعیت‌ها و محدودیت‌ها', result.prohibitions),
              _analysisGroup('اختیارها و مجوزها', result.permissions),
              _analysisGroup('استثناها و شروط', result.exceptions),
              _analysisGroup('مهلت‌های زمانی', result.deadlines),
              _analysisGroup('ارجاع به ماده/تبصره/بند', result.references),
              _analysisGroup('نهادها و مراجع نام‌برده‌شده', result.authorities),
              if (!result.hasFindings)
                const Text('هیچ‌یک از الگوهای شناخته‌شده پیدا نشد. این نتیجه به معنی بی‌اثر بودن ماده نیست.'),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () async {
                  final text = _analysisToText(result);
                  await Clipboard.setData(ClipboardData(text: text));
                  _showMessage('خلاصه تحلیل کپی شد.');
                },
                icon: const Icon(Icons.copy),
                label: const Text('کپی نتیجه تحلیل'),
              ),
            ]),
          )),
        ],
      ],
    );
  }

  String _analysisToText(ArticleAnalysis a) {
    final groups = <String, List<String>>{
      'تعهدها': a.obligations,
      'ممنوعیت‌ها': a.prohibitions,
      'اختیارها': a.permissions,
      'استثناها': a.exceptions,
      'مهلت‌ها': a.deadlines,
      'ارجاعات': a.references,
      'مراجع': a.authorities,
    };
    final out = StringBuffer('تحلیل قاعده‌محور اولیه قانون‌بان\n');
    for (final group in groups.entries) {
      out.writeln('\n' + group.key + ':');
      out.writeln(group.value.isEmpty ? 'موردی تشخیص داده نشد.' : group.value.join('\n'));
    }
    return out.toString();
  }

  Widget _analysisGroup(String title, List<String> values) {
    if (values.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(title, style: const TextStyle(fontWeight: FontWeight.bold, color: navy)),
        const SizedBox(height: 4),
        ...values.map((value) => Padding(
          padding: const EdgeInsets.only(bottom: 4),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('•  '),
            Expanded(child: Text(value)),
          ]),
        )),
      ]),
    );
  }

  Widget _settingsPage() {
    final local = _store;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('تنظیمات و حوزه‌های پیگیری',
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Card(child: SwitchListTile(
          secondary: const Icon(Icons.notifications_active_outlined),
          title: const Text('اعلان اندروید هنگام دریافت خبر تازه'),
          subtitle: Text(_notificationsReady
              ? 'در زمان بررسی موفق و بازبودن برنامه اعلان صادر می‌شود.'
              : 'مجوز اعلان یا سرویس اعلان در دسترس نیست؛ تاریخچه داخل برنامه فعال است.'),
          value: _notificationsEnabled,
          onChanged: (value) async {
            await local?.setNotificationsEnabled(value);
            if (value) await _initializeNotifications();
            if (mounted) setState(() => _notificationsEnabled = value);
          },
        )),
        const SizedBox(height: 8),
        const Text('حوزه‌هایی که برای آن‌ها هشدار ثبت شود:',
          style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 5),
        ...defaultWatchedCategories.map((category) => Card(
          child: CheckboxListTile(
            value: _watchedCategories.contains(category),
            title: Text(category),
            secondary: const Icon(Icons.track_changes),
            onChanged: (checked) async {
              final updated = _watchedCategories.toSet();
              if (checked == true) {
                updated.add(category);
              } else {
                updated.remove(category);
              }
              await local?.setWatchedCategories(updated);
              if (mounted) setState(() => _watchedCategories = updated);
            },
          ),
        )),
        const SizedBox(height: 12),
        Card(child: ListTile(
          leading: const Icon(Icons.cloud_done_outlined),
          title: const Text('منبع دریافت'),
          subtitle: const Text('پایگاه ملی اطلاع‌رسانی قوانین و مقررات • dotic.ir'),
          trailing: IconButton(
            tooltip: 'بازکردن منبع',
            icon: const Icon(Icons.open_in_browser),
            onPressed: () => _openOfficialUrl(officialHome),
          ),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.sync),
          title: const Text('آخرین دریافت موفق'),
          subtitle: Text(_lastChecked == null ? 'هنوز دریافت موفقی ثبت نشده' : _lastChecked!.toLocal().toString().substring(0, 16)),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.article_outlined),
          title: const Text('اسناد فعلی'),
          trailing: Text(_documents.length.toString()),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.bookmark_outline),
          title: const Text('اسناد ذخیره‌شده'),
          trailing: Text(_savedIds.length.toString()),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.notifications_none),
          title: const Text('تاریخچه هشدار'),
          trailing: Text(_alerts.length.toString()),
        )),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _loading ? null : refresh,
          icon: const Icon(Icons.refresh),
          label: const Text('بررسی دوباره منبع رسمی'),
        ),
        const SizedBox(height: 12),
        const Text(
          'محدودیت این نسخه: بررسی خودکار هر ۳۰ دقیقه فقط تا زمانی انجام می‌شود که سیستم‌عامل برنامه را فعال نگه دارد. برای پایش ۲۴ساعته و هشدار در زمان بسته‌بودن برنامه، سرور و سرویس Push لازم است. خبرها به‌صورت خودکار به‌عنوان قانون لازم‌الاجرا تأیید نمی‌شوند.',
          style: TextStyle(fontSize: 12, color: Colors.black54),
        ),
      ],
    );
  }

  Widget _emptyCard(String message, {required IconData icon}) => Card(
    child: Padding(
      padding: const EdgeInsets.all(18),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: Colors.black45),
        const SizedBox(width: 10),
        Expanded(child: Text(message)),
      ]),
    ),
  );

  Widget _currentPage() {
    switch (_tab) {
      case 0: return _feedPage();
      case 1: return _alertsPage();
      case 2: return _savedPage();
      case 3: return _analysisPage();
      default: return _settingsPage();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _autoRefreshTimer?.cancel();
    _search.dispose();
    _savedSearch.dispose();
    _articleInput.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Row(mainAxisSize: MainAxisSize.min, children: [
        Image.asset('assets/icon.png', width: 32, height: 32),
        const SizedBox(width: 8),
        const Text('قانون‌بان'),
      ]),
      actions: [
        IconButton(
          onPressed: _loading ? null : refresh,
          tooltip: 'دریافت تازه‌ترین خبرها',
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 780),
          child: RefreshIndicator(
            onRefresh: refresh,
            child: ListView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.all(16),
              children: [
                if (_loading) const LinearProgressIndicator(),
                if (_error.isNotEmpty)
                  Card(color: Colors.red.shade50, child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Icon(Icons.error_outline, color: Colors.red),
                      const SizedBox(width: 8),
                      Expanded(child: Text(_error, style: const TextStyle(color: Colors.red))),
                      IconButton(
                        tooltip: 'بستن پیام',
                        onPressed: () => setState(() => _error = ''),
                        icon: const Icon(Icons.close, size: 18),
                      ),
                    ]),
                  )),
                const SizedBox(height: 8),
                _currentPage(),
              ],
            ),
          ),
        ),
      ),
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: _tab,
      onDestinationSelected: (index) => setState(() => _tab = index),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.article_outlined), selectedIcon: Icon(Icons.article), label: 'مقررات'),
        NavigationDestination(icon: Icon(Icons.notifications_outlined), selectedIcon: Icon(Icons.notifications), label: 'هشدارها'),
        NavigationDestination(icon: Icon(Icons.bookmark_outline), selectedIcon: Icon(Icons.bookmark), label: 'ذخیره‌ها'),
        NavigationDestination(icon: Icon(Icons.manage_search), selectedIcon: Icon(Icons.search), label: 'تحلیل ماده'),
        NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'تنظیمات'),
      ],
    ),
  );
}
