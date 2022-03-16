/*
@synth
@shortdesc Some kind of additive synth?
@desc I really don't know what this one is.
@types Bass, Additive, Autorelease
*/
(
SynthDef('addpad', {arg freq=440,amp=0.4,dur=4,atk=0.1,rel=0.1,dt=0.01,dec=1,modhz=0.1,out=0;
	var sig, ratios, sc;
	sc = Scale.major;
	sc = Scale.minor;
	ratios = sc.ratios ++ (sc.ratios * 2) ++ (sc.ratios * 3) ++ (sc.ratios * 4) ++ (sc.ratios * 5);
	ratios = ratios.scramble;
	sig = Saw.ar(freq*ratios, mul: {SinOsc.ar(modhz,Rand(0.0,pi)).range(0.0,1.0)}.dup(ratios.size));
	sig = Splay.ar(sig) * 0.4;
	sig = sig + CombC.ar(sig, 0.5, dt, dec, mul: 0.2);
	sig = sig * EnvGen.ar(Env.linen(atk,dur-atk-rel,rel,curve: -4), doneAction: 2);
	Out.ar(out, LeakDC.ar(sig) * amp);
}).add;
);

/*
@example addpad
*/
Synth('addpad');
(instrument:'addpad', freq: "G4".notecps, dur: 3, amp: 0.5, atk: 0.01, rel: 0.2, modhz: 0.4).play;
(instrument:'addpad', freq: "C5".notecps, dur: 6, amp: 0.5, atk: 2, rel: 2, dt:0.01, dec:1).play;
(instrument:'addpad', freq: "Bb4".notecps, dur: 4, amp: 0.5, atk: 0.1, rel: 2, dec: 0).play;
(instrument:'addpad', freq: "A5".notecps, dur: 4, amp: 0.5, atk: 0.1, rel: 2, dec: 0).play;


/*
@synth
@shortdesc Sawtooth Resonant Filter
@desc Detuned Sawtooth Resonant Filter Pad
@types Pad, Autorelease, Resonant
*/
(
SynthDef('rezpad', {arg out=0, amp=0.3, freq=440, dur=1, phase=2.5, modhz=0.1, saturation=10, atk=0.1, tremhz=4, tremmix=0.4;
	var sig, mod, ratios;
	//ratios = Scale.sikah.ratios;
	//ratios = [400,5000,6000,200,300,560] / 400;
	ratios = [ 1.0665241742002, 1.0736323145277, 1.0792282365041, 1.0594630943591, 1.0293022366434 ];
	sig = Saw.ar(freq * ratios).sum;
	//mod = EnvGen.ar(Env.halfSine(1,0), timeScale: dur);
	mod = SinOsc.ar(modhz, phase * (pi/2)).range(0.2, 3);
	sig = Resonz.ar(sig, [400,1200,500,800,569] * mod, 0.1, mul: 1.0);
	sig = (sig*saturation).tanh * (saturation.log + 1).reciprocal;
	sig = Splay.ar(sig, Line.ar(0.1,1,dur/2)) * EnvGen.ar(Env.linen(atk,1-atk), doneAction: 2, timeScale: dur);
	sig = SinOsc.ar(tremhz).range(1-tremmix,1.0) * sig;
	Out.ar(out, sig * amp);
}).add;
);


/*
@example rezpad
*/
Synth('rezpad');
(instrument: \rezpad, freq: "A2".notecps, dur: 0.2, saturation: 7, amp: 0.5, modhz: 2, tremhz: 5, tremmix: 1, atk: 0.01).play;
(instrument: \rezpad, freq: "A3".notecps, dur: 3.25, saturation: 10, amp: 0.5, modhz: 0.01, phase: 2.5, atk: 0.1).play;
(instrument: \rezpad, freq: "G3".notecps, dur: 1.50, saturation: 100, amp: 0.5, modhz: 0.06, phase: 2.5, atk: 0.1).play;
(instrument: \rezpad, freq: "D4".notecps, dur: 3.25, saturation: 10, amp: 0.5, modhz: 0.04, phase: 2.5, atk: 0.1).play;
(instrument: \rezpad, freq: "G4".notecps, dur: 13.25, saturation: 10, amp: 0.5, modhz: 0.01, phase: 1.5, atk: 0.5).play;
(instrument: \rezpad, freq: "D5".notecps, dur: 13.25, saturation: 10, amp: 0.5, modhz: 0.01, phase: 1.5, atk: 0.5, tremhz: 2, tremmix: 0.2).play;





/*
@synth
@shortdesc Subtractive Sawtooth 1
@desc Simple Subtractive Sawtooth synth
@types Subtractive, Autorelease
*/
(
SynthDef('saw1', {arg freq=440, sync=300, out, pan=0, amp=0.5, dur=1, co1=4000, co2=800, rq=0.1, fold=0.5;
	var sig;
	sig = (SyncSaw.ar(freq + {LFNoise0.ar(10).range(-10.0,10.0)}!10, sync)*0.1).sum;
	sig = (sig).fold(-1 * fold, fold) * fold.reciprocal;
	sig = BLowPass4.ar(sig, XLine.ar(co1, co2, dur / 2), rq);
	sig = sig * EnvGen.ar(Env.perc, timeScale: dur, doneAction: 2);
	Out.ar(out, Pan2.ar(sig,pan) * amp);
}).add;
);


/*
@example saw1
*/
Synth('saw1');
(instrument:'saw1', freq: "G4".notecps, sync: 100, dur: 0.3, amp: 0.5, co1: 50, co2: 14000, rq: 0.1, fold: 0.8).play;
(instrument:'saw1', freq: "B4".notecps, sync: 888, dur: 0.3, amp: 0.5, co1: 13000, co2: 30, rq: 0.01, fold: 0.8).play;
(instrument:'saw1', freq: 800, sync: 100, dur: 0.1, amp: 1.0, co1: 30, co2: 1300, rq: 0.1, fold: 0.5).play;
(instrument:'saw1', freq: 50, sync: 300, dur: 0.2, amp: 1.0, co1: 10300, co2: 30, rq: 0.1, fold: 0.5).play;



/*
@synth
@shortdesc saw2
@desc Another simple Subtractive Sawtooth synth
@types Subtractive, Autorelease
*/
(
SynthDef('saw2', {arg freq=440, sync=400, out=0, pan=0, amp=0.5, dur=0.3, co=6000, rq=0.1;
	var sig;
	sig = SyncSaw.ar(freq, sync);
	sig = sig * EnvGen.ar(Env.perc, timeScale: dur, doneAction: 2);
	sig = RLPF.ar(sig, co, rq);
	sig = (sig*3).tanh;
	Out.ar(out, Pan2.ar(sig,pan) * amp);
}).add;
);

/*
@example saw2
*/
Synth('saw2');
(instrument:'saw2', freq: "G4".notecps, sync: 400, dur: 0.3, amp: 0.5, co: 5000, rq: 0.1).play;
(instrument:'saw2', freq: "C5".notecps, sync: 400, dur: 0.3, amp: 0.5, co: 3000, rq: 0.4).play;
(instrument:'saw2', freq: "D4".notecps, sync: 400, dur: 0.3, amp: 0.5, co: 2000, rq: 0.3).play;

(
Pdef('saw2', Pbind(*[
	instrument: 'saw2',
	degree: Pseq([0,2,4,6,9,5,11],inf,0),
	octave: 4,
	mtranspose: 3,
	dur: 0.1,
	delta: 0.2,
	amp: 0.3,
	co: 800,
	rq: 0.2,
	pan: 0,
	out: 0
])).play(quant: 1);
Pdef('saw22', Pbind(*[
	instrument: 'saw2',
	degree: Pseq([0,2,4,6,9,5,11],inf,0),
	octave: 4,
	mtranspose: Pn(Pseq([1,2,3,4,5,5].stutter(16)),inf),
	dur: 0.2,
	delta: 0.2,
	amp: 0.3,
	co: 6000,
	rq: 0.4,
	pan: 0,
	out: 0
])).play(quant: 1);
TempoClock.tempo = 100/60;
);

(
Ndef('mix', {
	var in,sig;
	in = InFeedback.ar(0, 2);
	sig = in;
	//sig = sig*0.5 + AllpassC.ar(sig, 1, 1/8, 1)*0.5;
	sig = (sig*1).tanh * 0.5;
	//sig = Decimator.ar(sig, 44100, 16);
	sig = GVerb.ar(sig, 100, 1, 1, 0.5, 1, 0.1, 0.3, 0.3, mul: 0.7) + sig;
	sig = LeakDC.ar(sig) * 0.5;
	ReplaceOut.ar(0, sig);
}).play(out:0, numChannels: 2);
);
Ndef('mix').clear(1);
Pdef.clear(1);


/*
@synth
@shortdesc Bass Thud
@desc Deep adjustable thud
@types Percussion, Deep, Additive
*/
(
SynthDef('thud', {arg freq=50,amp=1.0,dur=0.3,atk=0.1,rel=0.2,pan=0,dt=0.2,decay=0.3,out;
	var sig;
	sig = SinOsc.ar(freq*[0.88,0.9,1.0,1.01,1.2,1.3,1.4]).sum;

	sig = sig + CombC.ar(sig, 1, dt, decay);

	sig = FreeVerb.ar(sig, 0.1, 0.9, 0.9);

	sig = Compander.ar(sig, sig, 0.7, 0.1, 0.7, 0.1);

	sig = sig * EnvGen.ar(Env.linen(atk,dur-atk-rel,rel,curve: -4), doneAction: 2);
	Out.ar(out, Pan2.ar(sig, pan) * amp);
}).add;
);

/*
@example thud
*/
Synth('thud');
(instrument:'thud', freq: 40, dur: 0.3, amp: 0.5, dt: 0.1, decay: 0.1).play;
(instrument:'thud', freq: 30, dur: 1.0, atk: 0.01, rel: 0.5, amp: 0.5, dt: 0.1, decay: 0.1).play;



/*
@synth
@shortdesc Phase Modulation
@desc Phase Mod Synth
@types Autoreleae, Modulation
*/
(
SynthDef('pm', {arg freq=200, amp=1.0, pan=0.0, dur=1.0, out, ratio=1.5, idx=2.5, phase=0.5, co1=9000, co2=50, rq=0.5;
	var sig;
	sig = (PMOsc.ar(freq * Array.geom(10,1.0,1.01), freq * ratio, idx, phase) * 0.1);
	sig = sig.sum;

	sig = BLowPass4.ar(sig, XLine.ar(co1, co2, dur), rq);

	sig = sig * EnvGen.ar(Env.linen, timeScale: dur, doneAction: 2);
	Out.ar(out, Pan2.ar(sig, pan) * amp);
}).add;
);

/*
@example pm
*/
Synth('pm');
(instrument:'pm', freq: 2200, dur: 0.3, amp: 0.5, ratio: 1.3, idx: 2, phase: 0.0, out: 0).play;

(
Pdef('pm', Pbind(*[
	instrument: 'pm',
	degree: Pseq([Pseq([0,4,6,5],7), Pseq([11],20)],inf,0),
	octave: 3,
	mtranspose: Pseq([[0,5],[1,7]].stutter(5*8),inf),
	dur: 0.2,
	delta: Pseq([Pseq(0.25!4,8), Pseq([0.01],20)],inf,0),
	ratio: 2.1,
	idx: 1.5,
	amp: 0.4,
	co1: 8800,
	co2: 200,
	rq: 0.3,
	pan: Pwhite(-0.8,0.8),
	out: 0
])).play(quant: 1);

TempoClock.tempo = 120/60;
);


/*
@synth
@shortdesc Panning Phase Mod Synth
@desc Phase Modulation Synth with Panning Motion
@types Autorelease, Modulation
*/
(
SynthDef('pm2', {arg out,freq=180, sat=0.5, car_min=300, car_max=500, car_rate=0.5,pmidx=3.5,pmphase=0.7,amp=0.3,dur=0.5,
	co1=1300,co2=20, rq=1.0, pan_s=1, pan_e=(-1), wrap_l=(-1.0), wrap_h=1.0;
	var sig, pan, car=LFNoise2;
	//car = SinOsc;
	sig = PMOsc.ar(freq, car.ar(car_rate).range(car_min,car_max), pmidx, pmphase);
	sig = RHPF.ar(sig, XLine.ar(co1, co2, dur), rq);
	sig = (sig*sat).fold(wrap_l,wrap_h) * (sat.log2 + 2).reciprocal;
	pan = Line.ar(pan_s,pan_e,dur);
	sig = Pan2.ar(sig,pan) * EnvGen.ar(Env.perc, timeScale: dur, doneAction: 2);
	sig = LeakDC.ar(sig);
	Out.ar(out, sig * amp);
}).add;
);

/*
@example pm2
*/
Synth('pm2');
(instrument: \pm2, \wrap_l: -0.2, \wrap_h: 1, \amp: 0.5, \sat: 0.3, \dur: 2).play;

(
Pdef('pm2', Pbind(*[
	instrument: 'pm2',
	degree: Pseq([-10,0,2,4,6,8,12,14,19].reverse.stutter(1),2),
	scale: Scale.minor,
	octave: 3,
	car_min: Pwhite(20,200),
	car_max: Pwhite(20,4000),
	car_rate: Ndef('sin',{ Line.ar(100,1,1) }),
	sat: 30,
	wrap_l: -100,
	wrap_h: 100,
	dur: 0.2,
	delta: Pwhite(0.09,0.11),
	amp: 0.2,
	co1: 600,
	co2: 20,
	rq: 0.3,
	pmidx: 3.5,
	pmphase: 1.1,
	pan_s: 1,
	pan_e: -1,
	out: 0
])).play(quant: 1);
);


/*
@synth
@shortdesc rhodes
@desc Rhodes piano
@types Additive,Piano
*/
SynthDef('rhodes', {arg out = 0, freq = 440, gate = 1, pan = 0, amp = 0.1, sus=0.0, /*change sus for less Rhodesy sounds*/
    /*range from 0-1*/ vel = 0.8, modIndex = 0.2, mix = 0.2, lfoSpeed = 0.4, lfoDepth = 0.1,
	atk=0.001, rel=0.04;
    var env1, env2, env3, env4;
    var osc1, osc2, osc3, osc4, snd;

    lfoSpeed = lfoSpeed * 12;

	freq = freq * 2;

    env1 = EnvGen.ar(Env.adsr(atk, 1.25, sus, rel, curve: \lin));
    env2 = EnvGen.ar(Env.adsr(atk, 1.00, sus, rel, curve: \lin));
    env3 = EnvGen.ar(Env.adsr(atk, 1.50, sus, rel, curve: \lin));
    env4 = EnvGen.ar(Env.adsr(atk, 1.50, sus, rel, curve: \lin));

    osc4 = SinOsc.ar(freq * 0.5) * 2pi * 2 * 0.535887 * modIndex * env4 * vel;
    osc3 = SinOsc.ar(freq, osc4) * env3 * vel;
    osc2 = SinOsc.ar(freq * 15) * 2pi * 0.108819 * env2 * vel;
    osc1 = SinOsc.ar(freq, osc2) * env1 * vel;
    snd = Mix((osc3 * (1 - mix)) + (osc1 * mix));
    snd = snd * (SinOsc.ar(lfoSpeed) * lfoDepth + 1);

    // using the doneAction: 2 on the other envs can create clicks (bc of the linear curve maybe?)
    snd = snd * EnvGen.ar(Env.asr(0, 1, rel), gate, doneAction: 2);
    snd = Pan2.ar(snd, pan, amp);
    Out.ar(out, snd);
}).add;
