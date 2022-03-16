/*
@synth
@shortdesc Simple FM Gated
@desc Simple gated FM Synth
@types Event, Modulation, FM
*/
(
SynthDef('fm01', {arg freq=200, modhz=440, amp=0.3, gate=1, atk=0.01, rel=0.1, pan=0, out=0;
	var sig;
	var env1,env2,env3;

	// Synthesis
	sig = SinOsc.ar(freq, SinOsc.ar(modhz).range(0,2*pi));

	// Dynamics
	env1 = EnvGen.ar(Env.asr(atk, 1, rel), gate, doneAction: 2);
	sig = sig * env1;

	Out.ar(out, Pan2.ar(sig,pan,amp));
}).add;
);


/*
@example fm01
*/
(instrument:'fm01',dur:0.2).play;
(instrument:'fm01', freq: "G4".notecps, modhz: 830, dur: 0.2, amp: 0.5, atk: 0.01, rel: 0.3).play;
(instrument:'fm01', freq: "G3".notecps, modhz: 830, dur: 0.2, amp: 0.5, atk: 0.01, rel: 0.3).play;
(instrument:'fm01', freq: "G9".notecps, modhz: 830, dur: 0.1, amp: 0.3, atk: 0.01, rel: 0.3).play;
