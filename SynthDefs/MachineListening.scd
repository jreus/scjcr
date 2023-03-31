/*
@synth
@shortdesc Amplitude Trigger
@desc A listener that triggers when input amplitude is above a threshhold.
@types Machine Listening, Trigger, Tracking
*/
(
SynthDef('ampTrigger', {|inbus=0, preGain=1.0, tid, thresh=0.1, ctrlbus|
  var insig, amp, t_send;
  insig = SoundIn.ar([inbus,inbus+1]).sum;
  amp = Amplitude.kr(insig, 0.001, 0.01);
  t_send = Changed.kr(amp, thresh);
  SendTrig.kr(t_send, tid, amp);
  Out.kr(ctrlbus, amp);
}).add;
);