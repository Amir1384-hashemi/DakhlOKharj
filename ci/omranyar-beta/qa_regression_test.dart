import 'dart:math' as math;
import 'package:flutter_test/flutter_test.dart';
import 'package:omran_yar/core/math_engine.dart';
import 'package:omran_yar/core/civil_engine.dart';
import 'package:omran_yar/core/truss_engine.dart';

void main() {
  group('QA scientific engine independent reference checks', () {
    test('scientific precedence, trig, base logarithm, localized digits', () {
      final examples = <String, double>{
        '2+3*4': 14, '(2+3)*4': 20, '-2^2': -4,
        '2^3^2': 512, 'sqrt(81)': 9,
        'log(1000)': 3, 'ln(e)': 1,
        'logb(2,8)': 3, 'logb(0.5,8)': -3,
        '۳×(۴+۲)': 18, '٢+٣٫٥': 5.5,
      };
      for (final entry in examples.entries) {
        expect(MathEngine.calculate(entry.key), closeTo(entry.value, 1e-9), reason: entry.key);
      }
      expect(MathEngine.calculate('sin(30)'), closeTo(0.5, 1e-12));
      expect(MathEngine.calculate('cos(pi)', degrees:false), closeTo(-1, 1e-12));
      expect(() => MathEngine.calculate('tan(90)'), throwsFormatException);
    });
    test('undefined arithmetic and real-number domains are not masked', () {
      for (final input in ['0/0', '1/0', '0*(1/0)', '0/(x-x)', 'sqrt(-1)', 'ln(-1)', 'logb(1,8)', 'logb(2,0)']) {
        expect(() => MathEngine.calculate(input), throwsFormatException, reason: input);
      }
      expect(formatNum(1e-16), isNot('0'));
    });
    test('symbolic antiderivatives and point derivatives agree with known results', () {
      for (final f in ['3*x^2+2*x', 'sin(2*x)', 'cos(x)', 'exp(3*x)', '1/x', '(2*x+1)^3']) {
        final primitive = MathEngine.integrate(f);
        final integrand = MathEngine.parse(f);
        for (final x in [0.7, 1.1, 2.5]) {
          expect(primitive.derivative().eval(x), closeTo(integrand.eval(x), 1e-8), reason: f);
        }
      }
      expect(MathEngine.derivativeAt('x^3',2),closeTo(12,1e-9));
      expect(MathEngine.derivativeAt('logb(2,x)',2),closeTo(1/(2*math.ln2),1e-9));
    });
    test('definite integral is signed and rejects improper poles', () {
      expect(MathEngine.definiteIntegral('3*x^2',0,2),closeTo(8,1e-9));
      expect(MathEngine.definiteIntegral('3*x^2',2,0),closeTo(-8,1e-9));
      expect(MathEngine.definiteIntegral('sin(x)',0,math.pi),closeTo(2,1e-9));
      expect(MathEngine.definiteIntegral('1/x',1,math.e),closeTo(1,1e-9));
      for (final f in ['1/x', '1/(x-0.37)^2', '(x-0.37)^(-1)']) {
        expect(() => MathEngine.definiteIntegral(f,0,1),throwsFormatException,reason:f);
      }
      expect(() => MathEngine.definiteIntegral('tan(x)',0,math.pi),throwsFormatException);
    });
  });

  group('QA section geometry and conversion references', () {
    test('rectangle dimensions and polar inertia', () {
      final s=SectionResult.rectangle(300,500);
      expect(s.area,closeTo(150000,1e-7));
      expect(s.ix,closeTo(3125000000,1e-4));
      expect(s.iy,closeTo(1125000000,1e-4));
      expect(s.polarMoment,closeTo(4250000000,1e-4));
    });
    test('circular polar second moment and Saint-Venant torsion agree', () {
      final s=SectionResult.circle(100);
      expect(s.area,closeTo(2500*math.pi,1e-7));
      expect(s.ix,closeTo(math.pi*100000000/64,1e-6));
      expect(s.polarMoment,closeTo(s.torsionConstant!,1e-6));
    });
    test('open thinwall L and closed single-cell box', () {
      final open=ThinWallSection.calculate([
        const ThinWallSegment(0,0,100,0,2),
        const ThinWallSegment(100,0,100,80,2),
      ],closed:false);
      expect(open.area,closeTo(360,1e-8));
      expect(open.torsionConstant!,closeTo(480,1e-8));
      expect(open.polarMoment,closeTo(open.ix+open.iy,1e-8));
      final box=ThinWallSection.calculate([
        const ThinWallSegment(0,0,100,0,2),
        const ThinWallSegment(100,0,100,80,2),
        const ThinWallSegment(100,80,0,80,2),
        const ThinWallSegment(0,80,0,0,2),
      ],closed:true);
      expect(box.area,closeTo(720,1e-8));
      expect(box.cx,closeTo(50,1e-8));
      expect(box.cy,closeTo(40,1e-8));
      expect(box.torsionConstant!,closeTo(256000000/180,1e-6));
      expect(box.polarMoment,closeTo(box.ix+box.iy,1e-8));
    });
    test('invalid section geometry not silently accepted', () {
      expect(()=>SectionResult.rectangle(0,300),throwsFormatException);
      expect(()=>SectionResult.circle(-1),throwsFormatException);
      expect(()=>ThinWallSection.calculate([
        const ThinWallSegment(0,0,10,0,8)
      ],closed:false),throwsFormatException);
    });
    test('common civil units', () {
      expect(convertUnit('نیرو',1,'kN','N'),closeTo(1000,1e-12));
      expect(convertUnit('تنش و فشار',1,'MPa','Pa'),closeTo(1e6,1e-6));
      expect(convertUnit('گشتاور',3,'kN·m','N·m'),closeTo(3000,1e-12));
      expect(convertUnit('سطح',0.001,'m²','mm²'),closeTo(1000,1e-9));
      expect(convertUnit('طول',1,'ft','m'),closeTo(0.3048,1e-9));
      expect(()=>convertUnit('طول',1,'unknown','m'),throwsFormatException);
    });
  });

  group('QA beam load-support cases and equilibrium', () {
    test('point, UDL, triangular and trapezoid cases', () {
      final point=BeamResult(length:4,pointLoad:10,loadPosition:2,uniformLoad:0);
      expect(point.reactionLeft,closeTo(5,1e-9));
      expect(point.reactionRight,closeTo(5,1e-9));
      expect(point.moment(2),closeTo(10,1e-9));
      final udl=BeamResult(length:4,pointLoad:0,loadPosition:0,uniformLoad:2);
      expect(udl.reactionLeft,closeTo(4,1e-9));
      expect(udl.moment(2),closeTo(4,1e-9));
      final triangular=BeamResult(length:4,pointLoad:0,loadPosition:0,uniformLoad:0,linearWStart:0,linearWEnd:6);
      expect(triangular.reactionLeft,closeTo(4,1e-9));
      expect(triangular.reactionRight,closeTo(8,1e-9));
      final trapezoid=BeamResult(length:4,pointLoad:0,loadPosition:0,uniformLoad:0,linearWStart:2,linearWEnd:6);
      expect(trapezoid.reactionLeft,closeTo(20/3,1e-9));
      expect(trapezoid.reactionRight,closeTo(28/3,1e-9));
    });
    test('cantilevers have correct fixed-end force and moment', () {
      final left=BeamResult(length:4,pointLoad:10,loadPosition:4,uniformLoad:0,
        supportLeft:BeamSupport.fixed,supportRight:BeamSupport.free);
      expect(left.reactionLeft,closeTo(10,1e-9));
      expect(left.reactionMomentLeft,closeTo(40,1e-9));
      expect(left.moment(0),closeTo(-40,1e-9));
      expect(left.moment(4),closeTo(0,1e-9));
      final right=BeamResult(length:4,pointLoad:10,loadPosition:0,uniformLoad:0,
        supportLeft:BeamSupport.free,supportRight:BeamSupport.fixed);
      expect(right.reactionRight,closeTo(10,1e-9));
      expect(right.reactionMomentRight,closeTo(-40,1e-9));
      expect(right.moment(4),closeTo(-40,1e-9));
    });
    test('96 combined cases obey global equilibrium and moment-shear identity', () {
      for(var j=0; j<96; j++) {
        final L=4.0+j%8*0.37;
        final a=0.25*L, b=0.82*L;
        final p=0.1+(j%9)*1.3;
        final px=0.35*L;
        final udl=(j%7)*0.43;
        final w0=(j%6)*0.8, w1=(j%5)*1.1;
        final r=BeamResult(length:L,pointLoad:p,loadPosition:px,uniformLoad:udl,
          linearStart:a,linearEnd:b,linearWStart:w0,linearWEnd:w1);
        expect(r.reactionLeft+r.reactionRight,closeTo(r.totalLoad,1e-8),reason:'force $j');
        expect(r.reactionRight*L,closeTo(r.totalLoadMomentLeft,1e-8),reason:'moment $j');
        expect(r.moment(L),closeTo(0,1e-7),reason:'end moment $j');
        for (final f in [0.1,0.55,0.94]) {
          final x=f*L;
          final h=1e-5;
          final slope=(r.moment(x+h)-r.moment(x-h))/(2*h);
          expect(slope,closeTo(r.shear(x),1e-5),reason:'V=dM/dx $j $f');
        }
      }
    });
    test('unsupported and unstable support pairs reject analysis', () {
      for(final pair in [
        [BeamSupport.free,BeamSupport.free],
        [BeamSupport.fixed,BeamSupport.fixed],
        [BeamSupport.fixed,BeamSupport.roller],
        [BeamSupport.pinned,BeamSupport.pinned],
      ]) {
        expect(()=>BeamResult(length:4,pointLoad:10,loadPosition:2,uniformLoad:0,
           supportLeft:pair[0],supportRight:pair[1]), throwsFormatException);
      }
    });
  });

  group('QA truss global equilibrium, scaling and stability', () {
    TrussResult triangular(double load, double e) => TrussResult.analyze([
      const TrussNode('A',0,0,fixX:true,fixY:true),
      const TrussNode('B',4,0,fixY:true),
      TrussNode('C',2,3,fy:load),
    ], [
      TrussMember('A','B',areaMm2:2000,youngGpa:e),
      TrussMember('A','C',areaMm2:2000,youngGpa:e),
      TrussMember('B','C',areaMm2:2000,youngGpa:e),
    ]);
    test('known determinate truss: reactions and forces', () {
      final r=triangular(-10,200);
      expect(r.ry[0],closeTo(5,1e-7));
      expect(r.ry[1],closeTo(5,1e-7));
      expect(r.memberResults[0].axialKn,closeTo(10/3,1e-7));
      expect(r.memberResults[1].axialKn,closeTo(-5*math.sqrt(13)/3,1e-7));
      expect(r.equilibriumResidualKn,lessThan(1e-7));
    });
    test('truss response scales with loads and stiffness', () {
      final one=triangular(-10,200),twice=triangular(-20,200),soft=triangular(-10,100);
      for(var i=0;i<one.memberResults.length;i++){
        expect(twice.memberResults[i].axialKn,closeTo(2*one.memberResults[i].axialKn,1e-8));
        expect(soft.memberResults[i].axialKn,closeTo(one.memberResults[i].axialKn,1e-8));
      }
      expect(soft.dy[2],closeTo(2*one.dy[2],1e-9));
    });
    test('unstable or invalid trusses are refused', () {
      expect(()=>TrussResult.analyze([
        const TrussNode('A',0,0,fixX:true,fixY:true),
        const TrussNode('B',4,0,fy:-10),
      ],[const TrussMember('A','B',areaMm2:1000,youngGpa:200)]),throwsFormatException);
    });
  });
}