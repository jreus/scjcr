/*
@synth
@shortdesc Autorelease Pattern Grain Player
@desc A simple buffer playback synth with autorelease.
@types Granular, Buffer
*/
(
SynthDef('autograin', {|amp=0.5, dur=0.1, rate=1, pos=0, buf, out=0, pan=0, atk=0.001, sus=1, rel=0.001|
	var sig, play;
	atk = 0.01;
	rel = 0.01;
	sus = dur - atk - rel;
	pos = BufFrames.ir(buf) * pos;
	play = Line.ar(pos, pos + (dur * BufSampleRate.ir(buf) * rate), dur);
	sig = BufRd.ar(1, buf, play) * EnvGen.ar(Env.linen(atk, sus, rel), doneAction: 2);
	sig = Mix.ar(sig);
	Out.ar(out, Pan2.ar(sig, pan) * amp);
}).add;
);


/*
@synth
@shortdesc Gated Pattern Grain Player
@desc A simple buffer playback synth with gated release.
@types Granular, Buffer
*/
(
SynthDef('gategrain', {|amp=0.5, dur=0.25, rate=1, st=0, buf, out, pan=0, atk=0.01, rel=0.01, gate=1|
	var sig, head, playframes;
  playframes = SampleRate.ir * BufRateScale.kr(buf) * dur * rate;
	head = Line.ar(st, playframes+st, dur);
	sig = BufRd.ar(1, buf, head, 0.0) * EnvGen.ar(Env.asr(atk, 1.0, rel), gate, doneAction: 2);
	Out.ar(out, Pan2.ar(sig, pan, amp));
}).add;
);





/*
@synth
@shortdesc Cookiemonster Grainsynth
@desc Uses a GaussTrig instead of random impulse. DURATION is randomized via LFNoise1, borders are set with muldur, adddur. LFNoise1 folds to positiv with .abs, offset is added after, lfo is speed of LFNoise1. Env is randomized with PinkNoise. MouseX is playbackrate, MouseY is position in buffer. Blurred with PinkNoise, intensity via vrate and vpos. Buffers can be switched on the fly with x.set(\buf...  For Multichannel expansion get rid of Splay.ar and adapt !8 ~ buffer must be mono!
@types Event, Modulation, FM
*/
(
SynthDef('cookiemonster', {|gate = 1, dur_lfo_hz = 1.3, muldur = 1, adddur = 0.1,
	rate=1.0, vrate = 0.01, bufpos=0.5, vpos = 0.003, trigrate = 34, trigdev = 0.3,
	buf, amp = 0.1, out, dur=2, pan=0|
	var gdur, sig, env, atk, rel, gtrig;
	gtrig = GaussTrig.kr(trigrate, trigdev);
	gdur = LFNoise1.ar(dur_lfo_hz, muldur).abs + adddur;
	atk = dur * PinkNoise.ar(0.9,0.5);
	rel = dur-atk;
	env = EnvGen.ar(Env([0, 1, 0], [atk, rel], \sin, 1), gate, 1, doneAction: 2);
	rate = rate * PinkNoise.kr(vrate,1);
	bufpos = bufpos * PinkNoise.kr(vpos,1);
	sig = { BufGrain.ar(gtrig, gdur, buf, rate, bufpos, 4) * env }!8;
	//sig = Splay.ar(sig);
	sig = Pan2.ar(sig, pan);
	sig = LeakDC.ar(sig * amp);
	Out.ar(out,sig.tanh);
}).add;
);

/*
@example cookiemonster
Use mouse controls to explore
*/
~cm_buf = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav"); // remember to free the buffer later.
(instrument:'cookiemonster', buf: ~cm_buf, out: 0, dur: 3.0, bufpos: 0.5, vpos: 0.3).play;
(instrument:'cookiemonster', buf: ~cm_buf, out: 0, dur: 3.0, rate: Ndef(\cm_mousex, {MouseX.kr(0,5)}), bufpos: Ndef(\cm_mousey, {MouseY.kr(0,1)})).play;
(instrument:'cookiemonster', buf: ~cm_buf, out: 0, dur: 10.0, rate: 1.0, vrate: 0.2, bufpos: 0.6, vpos: 0.1, dur_lfo_hz: 3.0, muldur: 2.0, adddur: 0.1, trigrate: 50, trigdev: 2.0).play;

