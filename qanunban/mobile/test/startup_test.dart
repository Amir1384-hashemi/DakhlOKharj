import 'package:flutter_test/flutter_test.dart';
import 'package:qanunban/main.dart';
import 'package:qanunban/official_feed.dart';
Future<List<OfficialNews>> fixture() async => [
  const OfficialNews(id:'1501',title:'خبر جدید مقرراتی برای تست',
    url:'https://dotic.ir/news/1501',category:'سایر مقررات')
];
void main() {
  testWidgets('app opens without requiring a server or sign-in', (tester) async {
    await tester.pumpWidget(QanunBanApp(fetcher:fixture));
    await tester.pumpAndSettle();
    expect(find.text('قانون‌بان'), findsOneWidget);
    expect(find.text('آخرین خبرهای مقرراتی منتشرشده'),findsOneWidget);
    expect(tester.takeException(),isNull);
  });
}
