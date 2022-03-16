/*
@synth
@shortdesc Simple Karplus-Strong synth
@desc Triggered, percussive envelope Karplus-Strong synth
@types PhysicalModelling, Autorelease
*/
( // note: do not use frequencies less than 1
SynthDef('karp', {|freq=200, amp=0.5, atk=0.01, dec=0.04, rel=0.95, pan=0, dur=1, gate=1, decaytime=2, coef=0.1, out=0|
	var sig, env;
	sig = Pluck.ar(WhiteNoise.ar(1), gate, 1, freq.reciprocal, decaytime, coef);
	env = EnvGen.ar(Env.adsr(atk, dec, amp*0.8, rel), gate, doneAction: 2);
	Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;
);


/*
@example karp
*/
x = Synth('karp', ['dur', 20, 'freq', 220, 'amp', 1.0, 'decaytime', 0.5, 'coef', 0.1]);
y = Synth('karp', ['dur', 20, 'freq', 10, 'amp', 1.0, 'decaytime', 10.5, 'coef', 0.7, \delaytime, 0.6]);

