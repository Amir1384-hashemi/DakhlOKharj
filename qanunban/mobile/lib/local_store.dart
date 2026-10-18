import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';
import 'official_feed.dart';

const defaultWatchedCategories = <String>[
  'مالی و مالیاتی',
  'کار و بیمه',
  'حقوقی و قضایی',
  'بانک و تجارت',
  'سایر مقررات',
];

class AlertRecord {
  const AlertRecord({
    required this.id,
    required this.title,
    required this.url,
    required this.category,
    required this.createdAt,
    this.isRead = false,
  });

  final String id;
  final String title;
  final String url;
  final String category;
  final DateTime createdAt;
  final bool isRead;

  AlertRecord copyWith({bool? isRead}) => AlertRecord(
    id: id,
    title: title,
    url: url,
    category: category,
    createdAt: createdAt,
    isRead: isRead ?? this.isRead,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'url': url,
    'category': category,
    'createdAt': createdAt.toIso8601String(),
    'isRead': isRead,
  };

  factory AlertRecord.fromJson(Map<String, dynamic> json) => AlertRecord(
    id: json['id']?.toString() ?? '',
    title: json['title']?.toString() ?? '',
    url: json['url']?.toString() ?? '',
    category: json['category']?.toString() ?? 'سایر مقررات',
    createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
        DateTime.fromMillisecondsSinceEpoch(0),
    isRead: json['isRead'] == true,
  );
}

class QanunBanStore {
  QanunBanStore(this._prefs);

  final SharedPreferences _prefs;

  static Future<QanunBanStore> open() async =>
      QanunBanStore(await SharedPreferences.getInstance());

  bool get hasBaseline => _prefs.containsKey('known_ids_v2');

  Set<String> get knownIds =>
      (_prefs.getStringList('known_ids_v2') ?? const <String>[]).toSet();

  Future<void> setKnownIds(Iterable<String> ids) async {
    final values = ids.toSet().toList();
    // Bound local storage while preserving the most recently encountered IDs.
    final bounded = values.length > 1500
        ? values.sublist(values.length - 1500)
        : values;
    await _prefs.setStringList('known_ids_v2', bounded);
  }

  List<OfficialNews> get cachedDocuments {
    final raw = _prefs.getString('cached_documents_v2');
    if (raw == null) return const <OfficialNews>[];
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .whereType<Map<String, dynamic>>()
          .map(OfficialNews.fromJson)
          .toList();
    } catch (_) {
      return const <OfficialNews>[];
    }
  }

  Future<void> cacheDocuments(List<OfficialNews> documents) async {
    await _prefs.setString(
      'cached_documents_v2',
      jsonEncode(documents.map((d) => d.toJson()).toList()),
    );
  }

  Set<String> get savedIds =>
      (_prefs.getStringList('saved_document_ids_v2') ?? const <String>[])
          .toSet();

  Future<Set<String>> toggleSaved(String id) async {
    final saved = savedIds;
    if (!saved.add(id)) {
      saved.remove(id);
    }
    await _prefs.setStringList('saved_document_ids_v2', saved.toList());
    return saved;
  }

  Set<String> get watchedCategories {
    final values = _prefs.getStringList('watched_categories_v2');
    return values == null ? defaultWatchedCategories.toSet() : values.toSet();
  }

  Future<void> setWatchedCategories(Iterable<String> categories) async {
    await _prefs.setStringList('watched_categories_v2', categories.toSet().toList());
  }

  bool get notificationsEnabled =>
      _prefs.getBool('notifications_enabled_v2') ?? true;

  Future<void> setNotificationsEnabled(bool enabled) async {
    await _prefs.setBool('notifications_enabled_v2', enabled);
  }

  List<AlertRecord> get alerts {
    final raw = _prefs.getString('alert_history_v2');
    if (raw == null) return const <AlertRecord>[];
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .whereType<Map<String, dynamic>>()
          .map(AlertRecord.fromJson)
          .where((a) => a.id.isNotEmpty && a.title.isNotEmpty)
          .toList();
    } catch (_) {
      return const <AlertRecord>[];
    }
  }

  Future<void> addAlerts(Iterable<AlertRecord> newAlerts) async {
    final combined = <String, AlertRecord>{};
    for (final alert in [...newAlerts, ...alerts]) {
      combined.putIfAbsent(alert.id, () => alert);
    }
    final values = combined.values.toList()
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    final bounded = values.take(200).toList();
    await _prefs.setString(
      'alert_history_v2',
      jsonEncode(bounded.map((a) => a.toJson()).toList()),
    );
  }

  Future<void> markAlertRead(String id) async {
    final updated = alerts
        .map((a) => a.id == id ? a.copyWith(isRead: true) : a)
        .toList();
    await _prefs.setString(
      'alert_history_v2',
      jsonEncode(updated.map((a) => a.toJson()).toList()),
    );
  }

  Future<void> markAllAlertsRead() async {
    final updated = alerts.map((a) => a.copyWith(isRead: true)).toList();
    await _prefs.setString(
      'alert_history_v2',
      jsonEncode(updated.map((a) => a.toJson()).toList()),
    );
  }

  Future<void> clearAlerts() async {
    await _prefs.remove('alert_history_v2');
  }

  DateTime? get lastChecked {
    final millis = _prefs.getInt('last_successful_check_v2');
    return millis == null ? null : DateTime.fromMillisecondsSinceEpoch(millis);
  }

  Future<void> setLastChecked(DateTime value) async {
    await _prefs.setInt('last_successful_check_v2', value.millisecondsSinceEpoch);
  }
}
