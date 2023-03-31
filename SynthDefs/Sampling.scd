/*
@synth
@shortdesc 1ch sample playback
@desc 1-channel sample playback with start and endpoints specified in frames.
@types Sampler, Buffer, Monophonic
*/
(
SynthDef('sampleTrig1ch', {|amp=0.5, out=0, start=0, end, ratescale=1, buf, curve=0|
  var sig, head, dur;
  dur = ((end-start).abs) / (SampleRate.ir * BufRateScale.kr(buf));
  head = EnvGen.ar(Env([start,end], [dur * ratescale], curve), 1, doneAction: 2);
  sig = BufRd.ar(1, buf, head, 0);
  Out.ar(out, sig * amp);
}).add;
);

/*
@synth
@shortdesc 2ch sample playback
@desc 2-channel sample playback with start and endpoints specified in frames.
@types Sampler, Buffer, Stereo
*/
(
SynthDef('sampleTrig2ch', {|amp=0.5, out=0, start=0, end, ratescale=1, buf, curve=0|
  var sig, head, dur;
  dur = ((end-start).abs) / (SampleRate.ir * BufRateScale.kr(buf));
  head = EnvGen.ar(Env([start,end], [dur * ratescale], curve), 1, doneAction: 2);
  sig = BufRd.ar(2, buf, head, 0);
  Out.ar(out, sig * amp);
}).add;
);



/*
@synth
@shortdesc Monophonic Sampler
@desc Monophonic sampler, good for use with Pmono. A trigger to \t_reset changes immediately to another sample position or buffer.
@types Sampler, Buffer, Monophonic
*/
(
SynthDef('monophonicSampler1', {|amp=1, out=0, start=0, end, buf|
var sig, head, dur;
  dur = (end-start).abs / (SampleRate.ir * BufRateScale.kr(buf));
  head = EnvGen.ar(Env([0,start,end],[0,dur]), \t_reset.tr(1));
  sig = BufRd.ar(1, buf, head, 1.0);
  Out.ar(out, sig * amp)
}).add;
);

/*
@example monophonicSampler1
*/
x = Synth('monophonicSampler1');
x.set(\buf, 109, \start, 0, \end, 23090, \t_reset, 1);


/*
@synth
@shortdesc 1ch Pitch-based Sampler
@desc Sampler that can be used with the default event's frequency and duration controls. Start and end are in samples.
@types Sampler, Granular, Buffer, Gated, Looper
*/
(
SynthDef('pitchedSample1ch', {|amp=0.5, rate=1, freq=261.62556, rootPitch=261.62556, start=0, end, buf, out, pan=0, atk=0.01, rel=0.01, co=20000, rq=1.0, gate=1, loops=1|
var sig, playhead, dur, prate;
	var t_cnt = 0, t_loop = 0, mute=1;
	prate = rate * freq / rootPitch;
	dur = (end - start).abs / SampleRate.ir / BufRateScale.ir(buf) / prate;
	t_loop = Impulse.ar(dur.reciprocal);
	t_cnt = PulseCount.ar(t_loop);
	mute = (t_cnt <= loops);
	t_loop = mute * t_loop;
	playhead = EnvGen.ar(Env([0, start, end],[0, dur]), t_loop);
	sig = BufRd.ar(1, buf, playhead) * EnvGen.ar(Env.asr(atk, 1.0, rel), gate: gate, doneAction: 2);
	sig = Mix.ar(sig);
	sig = BLowPass4.ar(sig, co, rq);
	Out.ar(out, Pan2.ar(sig,pan) * amp);
}).add;
);



/*
@synth
@shortdesc 2ch Pitch-based Sampler
@desc Sampler that can be used with the default event's frequency and duration controls. Start and end are in samples.
@types Sampler, Granular, Buffer, Gated, Looper
*/
(
SynthDef('pitchedSample2ch', {arg amp=0.5, rate=1, freq=400, rootPitch=400, start=0, end, buf, out, gate=1, pan=0, atk=0.01, rel=0.01, co=2000, rq=1.0, loops=1;
	var sig, playhead, dur, prate;
	var t_cnt = 0, t_loop = 0, mute=1;
	prate = rate * freq / rootPitch;
	dur = (end - start).abs / SampleRate.ir / BufRateScale.ir(buf) / prate;
	t_loop = Impulse.ar(dur.reciprocal);
	t_cnt = PulseCount.ar(t_loop);
	mute = (t_cnt <= loops);
	t_loop = mute * t_loop;
	playhead = EnvGen.ar(Env([0, start, end],[0, dur]), t_loop);
	sig = BufRd.ar(2, buf, playhead) * EnvGen.ar(Env.asr(atk, 1.0, rel), gate: gate, doneAction: 2);
	sig = Mix.ar(sig);
	sig = BLowPass4.ar(sig, co, rq);
	Out.ar(out, Pan2.ar(sig,pan) * amp);
}).add;
);




