from pathlib import Path

path = Path("omranyar_flutter/lib/core/math_engine.dart")
source = path.read_text(encoding="utf-8")
replacements = [
    (
        "if (_zero(a) || _zero(b)) return NumExpr(0);",
        """if (_zero(a) || _zero(b)) {
    // Multiplication by zero cannot erase domain errors in the other term.
    final other = _zero(a) ? b : a;
    if (constant(other) != null) return NumExpr(0);
    return BinaryExpr('*', a, b);
  }""",
    ),
    (
        "if (_zero(a)) return NumExpr(0);",
        """if (_zero(a)) {
    // 0/0 and 0/x at x=0 are undefined; never silently reduce to 0.
    if (constant(b) != null && constant(b) != 0) return NumExpr(0);
    return BinaryExpr('/', a, b);
  }""",
    ),
    (
        "_rejectLinearZero(expr.right, lo, hi);",
        "_rejectPossibleDenominatorZero(expr.right, lo, hi);",
    ),
    (
        "if (n != null && n < 0) _rejectLinearZero(expr.left, lo, hi);",
        "if (n != null && n < 0) _rejectPossibleDenominatorZero(expr.left, lo, hi);",
    ),
    (
        "  static void _rejectLinearZero(MathExpr expr, double lo, double hi) {",
        """  static void _rejectPossibleDenominatorZero(MathExpr expr, double lo, double hi) {
    // Powers and products of affine factors inherit their real roots.
    if (expr is BinaryExpr && expr.op == '^') {
      final n = constant(expr.right);
      if (n != null && n > 0) {
        _rejectPossibleDenominatorZero(expr.left, lo, hi);
      }
      return;
    }
    if (expr is BinaryExpr && expr.op == '*') {
      _rejectPossibleDenominatorZero(expr.left, lo, hi);
      _rejectPossibleDenominatorZero(expr.right, lo, hi);
      return;
    }
    _rejectLinearZero(expr, lo, hi);
  }

  static void _rejectLinearZero(MathExpr expr, double lo, double hi) {""",
    ),
]
for old, new in replacements:
    if source.count(old) != 1:
        raise SystemExit(f"Unexpected source count {source.count(old)} for {old!r}")
    source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print("QA math-domain safeguards applied.")
