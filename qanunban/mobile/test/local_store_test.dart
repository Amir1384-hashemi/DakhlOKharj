import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:qanunban/local_store.dart';
import 'package:qanunban/official_feed.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('saved documents and watched categories persist', () async {
    final store = await QanunBanStore.open();
    expect(store.savedIds, isEmpty);
    final saved = await store.toggleSaved('1501');
    expect(saved, contains('1501'));
    await store.setWatchedCategories(['کار و بیمه']);
    expect(store.watchedCategories, {'کار و بیمه'});
  });

  test('alert history is stored, can be marked read and cleared', () async {
    final store = await QanunBanStore.open();
    await store.addAlerts([
      AlertRecord(
        id: '1501',
        title: 'خبر رسمی برای آزمون',
        url: 'https://dotic.ir/news/1501',
        category: 'سایر مقررات',
        createdAt: DateTime(2026, 1, 1),
      ),
    ]);
    expect(store.alerts, hasLength(1));
    expect(store.alerts.single.isRead, isFalse);
    await store.markAlertRead('1501');
    expect(store.alerts.single.isRead, isTrue);
    await store.clearAlerts();
    expect(store.alerts, isEmpty);
  });

  test('cached documents can be restored without network', () async {
    final store = await QanunBanStore.open();
    const item = OfficialNews(
      id: '1501',
      title: 'خبر رسمی برای آزمون',
      url: 'https://dotic.ir/news/1501',
      category: 'سایر مقررات',
    );
    await store.cacheDocuments([item]);
    expect(store.cachedDocuments.single.id, '1501');
    expect(store.cachedDocuments.single.title, item.title);
  });
}
