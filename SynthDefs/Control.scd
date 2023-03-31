/*
@synth
@shortdesc Dynamic Ramp
@desc A dynamically controllable continuous ramp generator.
@types Gesture, Control
*/
SynthDef('dynamicramp', {arg out=0, targetfreq=440, dur=0.0, curve=0, amp=0.1;
    var sig, rate, freq=440;
    var t_newtarget=1, oldtarget=0, newtarget=targetfreq;

    t_newtarget = Changed.kr(targetfreq);
    oldtarget = Latch.kr(freq, t_newtarget);
	freq = EnvGen.ar(Env([oldtarget,newtarget],[dur], curve), t_newtarget);

    sig = SinOsc.ar(freq);
    Out.ar(out, sig!2 * amp);
}).add;


/*
@example dynamicramp
*/
q = Synth('dynamicramp');

q.set(\targetfreq, 1000, \dur, 1.0, \curve, -4);
q.set(\targetfreq, 100, \dur, 3.0, \curve, \lin);

q.free;

