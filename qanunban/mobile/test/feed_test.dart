import 'package:flutter_test/flutter_test.dart';
import 'package:qanunban/official_feed.dart';
void main() {
  test('parse links from official origin; reject lookalike domains', () {
    final items = parseOfficialNews(
      '<a href="/news/1501">بخشنامه جدید مالیاتی شرکت‌ها</a>'
      '<a href="https://dotic.ir.evil.example/news/33">خبر جعلی مقرراتی</a>'
      '<a href="/news/1501">عنوان تکراری مجاز</a>'
    );
    expect(items.length,1);
    expect(items.first.id,'1501');
    expect(items.first.url,'https://dotic.ir/news/1501');
    expect(items.first.category,'مالی و مالیاتی');
  });
}
