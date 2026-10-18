import 'package:flutter_test/flutter_test.dart';
import 'package:qanunban/article_analyzer.dart';

void main() {
  test('extracts obligations, prohibitions, exceptions, deadlines and references', () {
    const text = 'ماده ۱۲: کارفرما مکلف است ظرف ۳۰ روز گزارش را ارسال کند. '
        'پرداخت جریمه ممنوع است مگر در موارد مقرر در تبصره ۲. '
        'وزارت موظف است نتیجه را اعلام کند.';
    final result = ArticleAnalyzer.analyze(text);
    expect(result.wordCount, greaterThan(10));
    expect(result.obligations, isNotEmpty);
    expect(result.prohibitions, isNotEmpty);
    expect(result.exceptions, isNotEmpty);
    expect(result.deadlines, contains('ظرف ۳۰ روز'));
    expect(result.references, contains('ماده ۱۲'));
    expect(result.references, contains('تبصره ۲'));
    expect(result.authorities, contains('وزارت'));
  });

  test('empty input does not invent legal findings', () {
    final result = ArticleAnalyzer.analyze('   ');
    expect(result.wordCount, 0);
    expect(result.hasFindings, isFalse);
  });
}
