import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:qanunban/main.dart';
import 'package:qanunban/official_feed.dart';

Future<List<OfficialNews>> fixture() async => [
  const OfficialNews(
    id: '1501',
    title: 'خبر جدید مقرراتی برای تست',
    url: 'https://dotic.ir/news/1501',
    category: 'سایر مقررات',
  ),
];

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('app opens, loads a feed item and renders all functional modules',
      (tester) async {
    await tester.pumpWidget(QanunBanApp(fetcher: fixture));
    await tester.pumpAndSettle();
    expect(find.text('قانون‌بان'), findsOneWidget);
    expect(find.text('آخرین خبرهای مقرراتی منتشرشده'), findsOneWidget);
    expect(find.text('خبر جدید مقرراتی برای تست'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('ذخیره‌ها'));
    await tester.pumpAndSettle();
    expect(find.text('اسناد ذخیره‌شده'), findsOneWidget);

    await tester.tap(find.text('تحلیل ماده'));
    await tester.pumpAndSettle();
    expect(find.text('تحلیل اولیه ماده یا تبصره'), findsOneWidget);

    await tester.tap(find.text('تنظیمات'));
    await tester.pumpAndSettle();
    expect(find.text('حوزه‌هایی که برای آن‌ها هشدار ثبت شود:'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
