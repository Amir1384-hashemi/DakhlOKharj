class ArticleAnalysis {
  const ArticleAnalysis({
    required this.wordCount,
    required this.obligations,
    required this.prohibitions,
    required this.permissions,
    required this.exceptions,
    required this.deadlines,
    required this.references,
    required this.authorities,
  });

  final int wordCount;
  final List<String> obligations;
  final List<String> prohibitions;
  final List<String> permissions;
  final List<String> exceptions;
  final List<String> deadlines;
  final List<String> references;
  final List<String> authorities;

  bool get hasFindings =>
      obligations.isNotEmpty ||
      prohibitions.isNotEmpty ||
      permissions.isNotEmpty ||
      exceptions.isNotEmpty ||
      deadlines.isNotEmpty ||
      references.isNotEmpty ||
      authorities.isNotEmpty;
}

class ArticleAnalyzer {
  static final RegExp _sentenceSplit = RegExp(r'(?<=[.!؟؛])\s+');
  static final RegExp _referencePattern = RegExp(
    r'(?:ماده|تبصره|بند|جزء)\s*[۰-۹0-9]+(?:\s*(?:و|تا|-)\s*[۰-۹0-9]+)?',
  );
  static final RegExp _deadlinePattern = RegExp(
    r'(?:ظرف|حداکثر تا|حداکثر ظرف|مهلت|در مدت|حداکثر مدت)\s*[۰-۹0-9]{1,3}\s*(?:روز کاری|روز|ماه|سال|ساعت|هفته)',
  );

  static const _obligationTerms = [
    'مکلف است', 'مکلفند', 'موظف است', 'موظفند', 'باید ', 'الزامی است',
    'ملزم است', 'ملزم می‌باشد',
  ];
  static const _prohibitionTerms = [
    'ممنوع است', 'ممنوع می‌باشد', 'حق ندارد', 'مجاز نیست', 'نمی‌تواند',
    'نباید ', 'منع می‌شود', 'غیرمجاز است',
  ];
  static const _permissionTerms = [
    'مجاز است', 'می‌تواند', 'اختیار دارد', 'اجازه دارد', 'بلامانع است',
  ];
  static const _exceptionTerms = [
    'مگر ', 'مگر آنکه', 'به استثنای', 'جز در موارد', 'با این حال',
    'مشروط بر اینکه', 'به‌جز',
  ];
  static const _authorityTerms = [
    'وزارت', 'سازمان', 'هیأت', 'هیات', 'دادگاه', 'دیوان', 'شورای',
    'بانک مرکزی', 'سازمان امور مالیاتی', 'تأمین اجتماعی', 'تامین اجتماعی',
    'وزیر', 'کمیسیون',
  ];

  static ArticleAnalysis analyze(String source) {
    final text = source.replaceAll(RegExp(r'\s+'), ' ').trim();
    final sentences = text
        .split(_sentenceSplit)
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toList();

    List<String> findSentences(List<String> terms) {
      final results = <String>[];
      for (final sentence in sentences) {
        if (terms.any((term) => sentence.contains(term)) &&
            !results.contains(sentence)) {
          results.add(sentence);
        }
      }
      return results;
    }

    List<String> findMatches(RegExp pattern) {
      final results = <String>{};
      for (final match in pattern.allMatches(text)) {
        final value = match.group(0)?.trim();
        if (value != null && value.isNotEmpty) results.add(value);
      }
      return results.toList();
    }

    final authorities = <String>{};
    for (final term in _authorityTerms) {
      if (text.contains(term)) authorities.add(term);
    }

    return ArticleAnalysis(
      wordCount: text.isEmpty ? 0 : text.split(' ').length,
      obligations: findSentences(_obligationTerms),
      prohibitions: findSentences(_prohibitionTerms),
      permissions: findSentences(_permissionTerms),
      exceptions: findSentences(_exceptionTerms),
      deadlines: findMatches(_deadlinePattern),
      references: findMatches(_referencePattern),
      authorities: authorities.toList(),
    );
  }
}
