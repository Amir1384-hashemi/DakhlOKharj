import 'package:flutter_test/flutter_test.dart';
import 'package:qanunban/official_feed.dart';

void main() {
  test('parse links from official origin and reject lookalike domains', () {
    final items = parseOfficialNews(
      '<article><a href="/news/1501">بخشنامه جدید مالیاتی شرکت‌ها</a>'
      '<p>متن خلاصه خبر رسمی برای آزمون</p></article>'
      '<a href="https://dotic.ir.evil.example/news/33">خبر جعلی مقرراتی</a>'
      '<a href="/news/1501">عنوان تکراری مجاز</a>'
    );
    expect(items.length, 1);
    expect(items.first.id, '1501');
    expect(items.first.url, 'https://dotic.ir/news/1501');
    expect(items.first.category, 'مالی و مالیاتی');
    expect(items.first.summary, contains('خلاصه'));
  });

  test('extracts article text while removing menus and scripts', () {
    final text = extractOfficialDocumentText(
      '<html><body><nav>منوی اصلی و پیوندهای ناوبری</nav>'
      '<main><article><h1>ماده یکم</h1>'
      '<p>وزارت مکلف است ظرف ۳۰ روز پس از دریافت درخواست، پاسخ مستدل را '
      'به متقاضی اعلام کند. این متن برای آزمون استخراج متن از صفحه رسمی است.</p>'
      '</article></main><script>secret fake text</script></body></html>',
    );
    expect(text, contains('وزارت مکلف است'));
    expect(text, isNot(contains('منوی اصلی')));
    expect(text, isNot(contains('secret fake text')));
  });
}
