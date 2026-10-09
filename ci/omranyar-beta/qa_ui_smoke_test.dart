import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:omran_yar/main.dart';
import 'package:omran_yar/pages/calculus_page.dart';
import 'package:omran_yar/pages/graph_page.dart';
import 'package:omran_yar/pages/civil_page.dart';

void main() {
  testWidgets('all main app tabs are accessible', (tester) async {
    await tester.pumpWidget(const OmranYarApp());
    expect(find.text('عمران‌یار'), findsOneWidget);
    for(final tab in ['ریاضیات', 'نمودار', 'عمران', 'ماشین‌حساب']){
      await tester.tap(find.text(tab).last);
      await tester.pumpAndSettle();
    }
    expect(find.text('logₐ'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('real calculator accepts input and calculates a nontrivial expression', (tester) async {
    await tester.pumpWidget(const OmranYarApp());
    await tester.enterText(find.byType(TextField).first, '2+3*4');
    await tester.ensureVisible(find.text('=').last);
    await tester.tap(find.text('=').last);
    await tester.pumpAndSettle();
    expect(find.text('14'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('custom base logarithm dialog calculates numeric result', (tester) async {
    await tester.pumpWidget(const OmranYarApp());
    await tester.ensureVisible(find.text('logₐ'));
    await tester.tap(find.text('logₐ'));
    await tester.pumpAndSettle();
    expect(find.text('پایه (a)'), findsOneWidget);
    await tester.tap(find.text('محاسبه'));
    await tester.pumpAndSettle();
    expect(find.byWidgetPredicate((w) => w is SelectableText && w.data == '3'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('definite integral upper/lower limits produce expected result', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CalculusPage())));
    await tester.enterText(find.byType(TextField).first, '3*x^2');
    await tester.tap(find.text('انتگرال معین'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('محاسبه'));
    await tester.tap(find.text('محاسبه'));
    await tester.pumpAndSettle();
    expect(find.textContaining('= 8'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('point derivative substitutes chosen x', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CalculusPage())));
    await tester.enterText(find.byType(TextField).first, 'x^3');
    await tester.tap(find.text('مشتق در نقطه'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('محاسبه'));
    await tester.tap(find.text('محاسبه'));
    await tester.pumpAndSettle();
    expect(find.textContaining('= 12'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('graph accepts three curves, warns on invalid curve and recovers', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: GraphPage())));
    expect(find.byType(CustomPaint), findsWidgets);
    expect(find.byType(Slider), findsOneWidget);
    await tester.enterText(find.byType(TextField).first, 'x^2-');
    await tester.ensureVisible(find.text('رسم نمودار'));
    await tester.tap(find.text('رسم نمودار'));
    await tester.pumpAndSettle();
    expect(find.text('تابع 1 نامعتبر است'), findsOneWidget);
    await tester.enterText(find.byType(TextField).first, 'x^2');
    await tester.tap(find.text('رسم نمودار'));
    await tester.pumpAndSettle();
    expect(find.text('تابع 1 نامعتبر است'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('beam load with triangular and support diagrams renders results', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CivilPage())));
    expect(find.text('RA'), findsOneWidget);
    expect(find.text('RB'), findsOneWidget);
    await tester.ensureVisible(find.text('بار گسترده خطی (مثلثی یا ذوزنقه‌ای)'));
    await tester.tap(find.text('بار گسترده خطی (مثلثی یا ذوزنقه‌ای)'));
    await tester.pumpAndSettle();
    expect(find.text('شدت ابتدا w(a)'), findsOneWidget);
    expect(find.text('W خطی'), findsOneWidget);
    expect(find.text('x̄ از چپ'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('thinwall open and closed sections report polar inertia and torsion', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CivilPage())));
    await tester.tap(find.text('مشخصات مقطع'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Ip = Ix + Iy'), findsOneWidget);
    for(final name in ['جدارنازک باز','جدارنازک بسته']){
      await tester.tap(find.text(name));
      await tester.pumpAndSettle();
      expect(find.textContaining('Jt ='), findsOneWidget);
      expect(find.textContaining('Ixy ='), findsOneWidget);
      expect(find.textContaining('Ip = Ix + Iy'), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('unit conversion returns 1000 N for 1 kN', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CivilPage())));
    await tester.tap(find.text('تبدیل واحد'));
    await tester.pumpAndSettle();
    expect(find.text('1000 N'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('editable truss shows output, warns on malformed nodes, then restores', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: CivilPage())));
    await tester.tap(find.text('تحلیل خرپا'));
    await tester.pumpAndSettle();
    expect(find.text('نیروهای محوری اعضا'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('truss_nodes')), 'invalid');
    await tester.pumpAndSettle();
    expect(find.textContaining('باید 7 ستون'), findsOneWidget);
    await tester.ensureVisible(find.text('بازگرداندن مثال خرپای مثلثی'));
    await tester.tap(find.text('بازگرداندن مثال خرپای مثلثی'));
    await tester.pumpAndSettle();
    expect(find.text('نیروهای محوری اعضا'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
