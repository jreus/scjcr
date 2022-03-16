/*
@synth
@shortdesc Simple gated sine oscillator with amplitude modulation
@desc Gated, autoreleasing sine oscillator
@types Gated, Minimal, Sine, Autorelease, AM
*/
(
SynthDef('sin1', {|out=0, freq=440, amp=0.1, gate=1, atk=1, rel=1, curve=0, mod=0, mute=0, pan=0|
  var sig;
  sig = SinOsc.ar(freq);
  sig = sig * (1 - SinOsc.ar(mod, mul: (mod > 0)).range(0.0,1.0));
  sig = sig * EnvGen.ar(Env.asr(atk, 1.0, rel, curve), gate, doneAction: 2);
  sig = Pan2.ar(sig, pan, amp);
  Out.ar(out, sig * (1-mute));
}).add;
);

/*
@example sin1
*/
x = Synth('sin1');
x.set(\gate, 0);



/*
@synth
@shortdesc Gated dual sine
@desc Gated, autoreleasing dual sine oscillators tuned based on a ratio. Separately controllable level, pan and amplitude modulation.
@types Gated, Minimal, Sine, Autorelease
*/
(
SynthDef('sin2', {|out=0, freq=440, fratio=1.2599, level1=0.5, level2=0.5,
  spread=0, center=0, amp=0.1, gate=1, atk=1, rel=1, curve=0, mod1=0, mod2=0, mute=0|
  var s1, s2, sig, freq2 = freq * fratio;
  s1 = SinOsc.ar(freq) * level1;
  s1 = s1 * (1 - SinOsc.ar(mod1, mul: (mod1 > 0)).range(0.0,1.0));
  s2 = SinOsc.ar(freq2) * level2;
  s2 = s2 * (1 - SinOsc.ar(mod2, mul: (mod2 > 0)).range(0.0,1.0));
  sig = Splay.ar([s1,s2], spread, 1, center);
  sig = sig * EnvGen.ar(Env.asr(atk, 1.0, rel, curve), gate, doneAction: 2);
  Out.ar(out, sig * amp * (1-mute));
}).add;
);

/*
@example sin2
*/
x = Synth('sin2');
x.set(\gate, 0);

(instrument: \sin2, dur: 2).play;

(instrument: \sin2, freq: "G3".f, fratio: 1.5, rel: 0.01, amp: 0.2, mod1: 1, mod2: 10, dur: 10).play;

(instrument: \sin2, freq: "Bb4".f, fratio: 1.5, rel: 0.01, amp: 0.1, mod1: 3.01, mod2: 4, dur: 4).play;

(instrument: \sin2, freq: "A4".f, fratio: 1.5, rel: 1, amp: 0.1, mod1: 3.01, mod2: 4, dur: 4, spread: 0.5).play;


(instrument: \sin2, freq: "E5".f, dur: 2, atk: 0.001, rel: 0.1, amp: 0.5, mod1: 15, mod2: 15.5, spread: 1).play;

(instrument: \sin2, freq: "D2".f, fratio: 1.75, dur: 4, atk: 0.01, rel: 0.01, amp: 1, mod1: 15, mod2: 25, spread: 0.5).play;

(instrument: \sin2, freq: "D6".f, rel: 1, amp: 0.1, mod1: 3.01, mod2: 4, dur: 4, spread: 1).play;



/*
@synth
@shortdesc Chaotic Noise Gesture 1
@desc Autoreleasing stereo chaotic noise gestures using Henon oscillator. Pans left to right.
@types Noise, Stereo, Autorelease, Chaos
*/
(
SynthDef('chao1', {|out=0, dur=0.5, amp=0.5, p1=1.23, p2=0.2, st_rate=10, end_rate=10, srange=1000, erange=22050, pan=0, autopan=0|
  var sig;
  sig = HenonC.ar(LFNoise0.ar(XLine.ar(st_rate, end_rate, dur)).range(srange, erange), p1, p2, 0, 0);
  sig = sig * EnvGen.ar(Env.linen(0.01, 0.98, 0.01), 1, timeScale: dur, doneAction: 2);
  pan = Select.kr((autopan <> 0), [pan, Line.kr(-1,1,dur), pan]);
  sig = Limiter.ar(LeakDC.ar(sig * amp), 1.0, 0.001);
  Out.ar(out, Pan2.ar(sig, pan));
}).add;
);

/*
@example chao1
*/
Synth(\chao1);

(instrument: \chao1, pan: -1, autopan: 0).play;
(instrument: \chao1, pan: -1, autopan: 1).play;

(instrument: \chao1, dur: 0.1, st_rate: 8, p1: 1.23, p2: 0.15).play;

(instrument: \chao1, dur: 0.4, st_rate: 5, end_rate: 5, p1: 1.24, p2: 0.21).play;

(instrument: \chao1, dur: 3.4, st_rate: 0.32, end_rate: 20, p1: 1.21, p2: 0.21).play;


/*
@synth
@shortdesc Chaotic Noise Gesture 2
@desc Tonal stereo chaotic noise gestures using Henon oscillator.
@types Noise, Stereo, Gated, Chaos
*/
(
SynthDef('chao2', {|out=0, amp=0.5, p1=1.23, p2=0.22,
  st_rate=10, dur=1, end_rate=10,
  freq=1202, atk=0.01, rel=0.01, gate=1, pan=0,
  panner=0, panrate=10|
  var sig;
  sig = HenonC.ar(freq, p1, p2, 0, 0);
  sig = sig * EnvGen.ar(Env.asr(atk, 1.0, rel), gate, doneAction: 2);
  sig = Limiter.ar(LeakDC.ar(sig * amp), 1.0, 0.001);
  pan = Select.kr(panner, [pan, Pulse.ar(panrate).range(-1,1)]);
  Out.ar(out, Pan2.ar(sig, pan, amp));
}).add;
);

/*
@example chao2
*/
(instrument: \chao2).play;

(instrument: \chao2, dur: 1.4, freq: 2000, p1: 1.23, p2: 0.15, out: 0).play;

(instrument: \chao2, freq: 1202, dur: 5, p1: 1.23, p2: 0.22, atk: 2, rel: 2).play;


(instrument: \chao2, freq: "C7".f, dur: 3.4, p1: 1.21, p2: 0.24, rel: 2).play;

(instrument: \chao2, freq: "A6".f, dur: 3.4, p1: 1.20, p2: 0.23, rel: 2).play;

